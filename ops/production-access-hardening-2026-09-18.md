# SkillHub 生产访问加固（2026-09-18）

## 变更目标

1. 生产 Web 发布端口 `18088` 仅允许来源 `10.0.0.0/8` 访问，其他来源拒绝。
2. 生产 Server 设置 `SESSION_COOKIE_SECURE=true`，使会话 Cookie 仅通过 HTTPS 发送。
3. 生产 Server 设置 `SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED=false`，关闭新本地账号注册，同时保留现有 `admin` 本地登录和改密能力。

## 当前链路

```text
用户浏览器
  -> HTTPS <PRIVATE_REGISTRY_HOST>
  -> 反向代理 <PROXY_HOST>
  -> HTTP <PRODUCTION_HOST>:18088
  -> Docker Web 容器:80
  -> Docker Server 容器:8080
```

`SESSION_COOKIE_SECURE=true` 约束的是浏览器到 `<PRIVATE_REGISTRY_HOST>` 的 HTTPS 请求。反向代理到生产机的内网 HTTP 回源不影响 Secure Cookie，因为 Cookie 在代理转发前已由浏览器基于外部 HTTPS URL 决定是否发送。

## 防火墙实现

- Git 源码：`ops/apply-firewall-hardening.sh`
- 生产副本：`/opt/skillhub/ops/apply-firewall-hardening.sh`
- systemd 单元：`skillhub-firewall-hardening.service`
- 规则链：`DOCKER-USER`
- 默认入口网卡：`ens7`
- 默认允许网段：`10.0.0.0/8`
- 默认发布端口：`18088`

Docker 在流量进入 `DOCKER-USER` 前已将宿主机 `18088` DNAT 为容器 `80`。因此规则使用 conntrack 的 `--ctorigdstport 18088` 匹配原始发布端口，不能只写 `--dport 18088`。

脚本可重复执行，并在添加新规则前删除其自身管理的旧规则，不修改 `DOCKER-USER` 中其他来源的规则。

## Cookie 配置实现

- 真实生产配置：`/opt/skillhub/shared/env.release`
- 配置项：`SESSION_COOKIE_SECURE=true`
- Git 中的 release 示例与 compose 模板也默认使用 `true`。

修改 `env.release` 后必须重建 `skillhub-server-1` 容器。`docker restart` 和 `/opt/skillhub/ops/restart.sh` 只重启现有容器，不会重新读取 env 文件。

## 本地注册门禁

- 真实生产配置：`/opt/skillhub/shared/env.release`
- 配置项：`SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED=false`
- 受影响接口：`POST /api/v1/auth/local/register` 返回 HTTP 403。
- 不受影响接口：`POST /api/v1/auth/local/login`、`POST /api/v1/auth/local/change-password`、钉钉登录。

该门禁由后端强制执行，不能只隐藏前端注册按钮。修改配置后必须重建 Server 容器，发布验证脚本只检查容器的有效环境变量，不会向生产注册接口发送有副作用的请求；接口返回 403 的行为由应用测试覆盖，生产环境如需人工验证应在隔离的 staging 环境执行。

使用当前 Server 镜像重新执行标准 server release：

```bash
/opt/skillhub/ops/deploy-release.sh \
  --component server \
  --server-tag <当前-server-tag> \
  --scanner-tag <当前-scanner-tag> \
  --apply
```

## 必须联动的事项

### 反向代理来源地址变化

当前代理位于允许的 `10.0.0.0/8`。如果后续代理迁移到非 `10/8` 地址、使用云负载均衡公网回源、或经过会改变源地址的 NAT，必须先调整 `SKILLHUB_WEB_ALLOWED_CIDR` 或防火墙规则，再切换代理，否则新代理会收到连接超时。

允许整个 `10.0.0.0/8` 可减少普通内网 IP 调整带来的上线变更，但也意味着任意已进入该内网的主机都能直接访问 `18088`。边界防护仍依赖内网隔离、主机访问控制和应用认证；若代理地址长期稳定，后续应收紧为代理主机或代理子网。

### HTTPS 与直接 HTTP 访问

启用 Secure Cookie 后：

- 对外 HTTPS registry 地址的登录和会话正常。
- 直接访问 `<PRODUCTION_HOST>:18088` 时，浏览器不会发送会话 Cookie，因此不能用该地址验证已登录页面。
- 健康检查、无需登录的首页和服务端接口探活不受影响。
- 若反向代理未正确传递 `X-Forwarded-Proto: https`，认证跳转或 Cookie 行为可能异常，应同步检查代理配置。

已有浏览器会话不一定会立即获得带 Secure 属性的新 Cookie。上线验证应包含一次重新登录；如需强制所有旧会话失效，应另行安排会话清理，不与本次配置变更混做。

### 配置与运行态同步

- 仓库脚本修改后运行 `./ops/sync-to-runtime.sh`，把 Git 版本同步到 `/opt/skillhub/ops`。
- 防火墙由 systemd 单元在开机时重新应用；只手工执行一次 `iptables` 命令不具备持久性。
- `env.release` 和备份文件包含运行配置，不得提交 Git。

## 上线步骤

1. 备份 `/opt/skillhub/ops/apply-firewall-hardening.sh` 和 `/opt/skillhub/shared/env.release`。
2. 同步新版防火墙脚本，执行 `bash -n`。
3. 重启 `skillhub-firewall-hardening.service`。
4. 检查 `DOCKER-USER` 中存在 `10.0.0.0/8` 的允许规则和其后的拒绝规则。
5. 从 `10/8` 主机验证 `<PRODUCTION_HOST>:18088` 可访问；从非 `10/8` 来源验证被拒绝。
6. 将生产 `SESSION_COOKIE_SECURE` 设置为 `true`。
7. 以当前镜像标签执行 server release，重建 Server 容器。
8. 验证容器环境、健康检查、HTTPS 首页、钉钉登录入口和重新登录后的 Cookie 属性。
9. 验证本地注册接口返回 403，现有 `admin` 本地登录仍可用。

## 验证命令

```bash
sudo iptables -S DOCKER-USER
systemctl is-enabled skillhub-firewall-hardening.service
systemctl is-active skillhub-firewall-hardening.service
docker inspect skillhub-server-1 --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | grep -E '^(SESSION_COOKIE_SECURE=true|SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED=false)$'
curl -fsS http://127.0.0.1:8080/actuator/health
# Do not POST to the production registration endpoint as an acceptance probe.
# The request can create a real account if the effective configuration is wrong.
# Use the application test suite or an isolated staging environment instead.
curl -fsSI "${SKILLHUB_PUBLIC_BASE_URL}/"
```

非 `10/8` 拒绝验证必须从真实的非 `10/8` 来源发起；在生产机本机测试不会经过相同入口路径，不能替代外部验证。

## 回滚

### 防火墙

恢复备份脚本并重启服务：

```bash
sudo cp <备份脚本> /opt/skillhub/ops/apply-firewall-hardening.sh
sudo systemctl restart skillhub-firewall-hardening.service
```

### Cookie

将 `/opt/skillhub/shared/env.release` 恢复为备份版本，再用相同 Server 镜像标签执行一次 server release。仅执行 `docker restart` 无法回滚容器环境变量。

### 本地注册

如业务确认需要恢复注册，将 `SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED=true`，再重建 Server 容器。恢复前应确认注册来源、账号审批和滥用防护要求；不要只修改前端入口。

## 本次实施记录

- 实施时间：2026-09-18 UTC
- 配置备份标识：`20260918T072023Z`
- 防火墙脚本备份：`/opt/skillhub/ops/apply-firewall-hardening.sh.bak-20260918T072023Z`
- 环境文件备份：`/opt/skillhub/shared/env.release.bak-20260918T072023Z`
- Server 镜像保持不变：`skillhub-server:prod-local-20260826T0902-validation-fix`
- 新 release：`/opt/skillhub/releases/20260918T072054Z`
- 验证结果：`10/8` 来源直连 `18088` 返回 200，非 `10/8` 来源连接超时，公网 HTTPS 返回 200。
- Cookie 验证：钉钉认证入口返回的 `SESSION` Cookie 已包含 `Secure; HttpOnly; SameSite=Lax`。
- 持久化验证：`skillhub-firewall-hardening.service` 为 `enabled` 且 `active`；脚本重复执行后允许和拒绝规则各仅一条。
- 本地注册配置备份：`/opt/skillhub/shared/env.release.bak-20260918T093720Z-local-registration`
- 本地注册关闭镜像：`skillhub-server:prod-local-20260918T093456Z-local-registration-disabled`
- 本地注册关闭 release：`/opt/skillhub/releases/20260918T094503Z`
- 注册验证：有效格式的 `POST /api/v1/auth/local/register` 返回 HTTP 403；现有 `docker-admin` 本地凭据保持 ACTIVE，钉钉认证入口返回 302。
- Token 验证：发布前后均为 41 条（ACTIVE 29、REVOKED 10、EXPIRED 2），本次发布未创建、吊销或修改 Token。
