#!/usr/bin/env bash
set -euo pipefail

CHAIN=${SKILLHUB_HARDEN_CHAIN:-DOCKER-USER}
IFACE=${SKILLHUB_HARDEN_IFACE:-ens7}
MONITOR_HOST=${SKILLHUB_MONITOR_HOST:-}
WEB_ALLOWED_CIDR=${SKILLHUB_WEB_ALLOWED_CIDR:-10.0.0.0/8}
WEB_PUBLISHED_PORT=${SKILLHUB_WEB_PUBLISHED_PORT:-18088}

if [ -z "$MONITOR_HOST" ]; then
  echo 'SKILLHUB_MONITOR_HOST must be set' >&2
  exit 2
fi

iptables -N "$CHAIN" 2>/dev/null || true

delete_rule() {
  while iptables -C "$CHAIN" "$@" 2>/dev/null; do
    iptables -D "$CHAIN" "$@"
  done
}

# Remove previously managed rules without touching unrelated DOCKER-USER rules.
delete_rule -i "$IFACE" -p tcp --dport 9100 -m string --algo bm --string "GET /debug/pprof" -m comment --comment "drop node exporter pprof" -j DROP
delete_rule -i "$IFACE" -p tcp --dport 9100 -s "$MONITOR_HOST" -m comment --comment "allow node exporter from prometheus" -j RETURN
delete_rule -i "$IFACE" -p tcp --dport 9100 -m comment --comment "drop direct node exporter 9100" -j DROP
delete_rule -i "$IFACE" -p tcp --dport 8080 -s "$MONITOR_HOST" -m comment --comment "allow skillhub server from monitoring host" -j RETURN
delete_rule -i "$IFACE" -p tcp --dport 8080 -m comment --comment "drop direct skillhub server 8080" -j DROP
delete_rule -i "$IFACE" -p tcp -m conntrack --ctorigdstport "$WEB_PUBLISHED_PORT" -s "$WEB_ALLOWED_CIDR" -m comment --comment "allow skillhub web from internal network" -j RETURN
delete_rule -i "$IFACE" -p tcp -m conntrack --ctorigdstport "$WEB_PUBLISHED_PORT" -m comment --comment "drop direct skillhub web published port" -j DROP

# Insert managed rules ahead of unrelated rules. Appending here would allow an
# earlier broad ACCEPT in DOCKER-USER to bypass the restrictions below.
# Insert in reverse order so the final chain order remains easy to audit.
iptables -I "$CHAIN" 1 -i "$IFACE" -p tcp -m conntrack --ctorigdstport "$WEB_PUBLISHED_PORT" -m comment --comment "drop direct skillhub web published port" -j DROP
iptables -I "$CHAIN" 1 -i "$IFACE" -p tcp -m conntrack --ctorigdstport "$WEB_PUBLISHED_PORT" -s "$WEB_ALLOWED_CIDR" -m comment --comment "allow skillhub web from internal network" -j RETURN
iptables -I "$CHAIN" 1 -i "$IFACE" -p tcp --dport 8080 -m comment --comment "drop direct skillhub server 8080" -j DROP
iptables -I "$CHAIN" 1 -i "$IFACE" -p tcp --dport 8080 -s "$MONITOR_HOST" -m comment --comment "allow skillhub server from monitoring host" -j RETURN
iptables -I "$CHAIN" 1 -i "$IFACE" -p tcp --dport 9100 -m comment --comment "drop direct node exporter 9100" -j DROP
iptables -I "$CHAIN" 1 -i "$IFACE" -p tcp --dport 9100 -s "$MONITOR_HOST" -m comment --comment "allow node exporter from prometheus" -j RETURN
# Block pprof even for otherwise allowed monitoring sources; keep /metrics available.
iptables -I "$CHAIN" 1 -i "$IFACE" -p tcp --dport 9100 -m string --algo bm --string "GET /debug/pprof" -m comment --comment "drop node exporter pprof" -j DROP
