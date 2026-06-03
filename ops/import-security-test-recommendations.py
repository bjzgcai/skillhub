#!/usr/bin/env python3
"""Import curated security-test recommendations into the production SkillHub registry.

This is a one-off operational migration script. It reconstructs minimal bundles from
metadata in the security test database, writes them to production storage, upserts
production skill/version/search/recommendation records, and marks their source as
`clawhub` via `remote_mirror_record`.

Safety: the script is write-heavy and intentionally requires `--apply`.
"""
import hashlib
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

TEST_DB = "skillhub_security_test"
PROD_DB = "skillhub"
TEST_PG = ["docker", "exec", "skillhub-security-test-postgres-1", "psql", "-U", "skillhub", "-d", TEST_DB]
PROD_PG = ["docker", "exec", "-i", "skillhub-postgres-1", "psql", "-U", "skillhub", "-d", PROD_DB, "-v", "ON_ERROR_STOP=1"]
ACTOR = "openclaw-rollout"
OWNER = "docker-admin"
PROD_VOLUME = "skillhub_skillhub_storage"


def run(cmd, input_text=None, capture=True):
    return subprocess.run(cmd, input=input_text, text=True, check=True, capture_output=capture).stdout if capture else subprocess.run(cmd, input=input_text, text=True, check=True)


def sql_literal(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def psql_json(cmd, sql):
    out = run(cmd + ["-Atc", sql])
    if not out.strip():
        return []
    return json.loads(out)


def normalize_badge(badge):
    if not badge:
        return None
    badge = badge.strip()
    if "/" in badge:
        return badge.split("/", 1)[1].strip() or None
    if badge.startswith("#"):
        return None
    return badge


def build_bundle(tmpdir, slug, version, metadata):
    body = metadata.get("body") or f"# {metadata.get('displayName') or slug}\n\n{metadata.get('summary') or ''}\n"
    meta = {
        "slug": slug,
        "name": metadata.get("name") or slug,
        "displayName": metadata.get("displayName") or metadata.get("name") or slug,
        "summary": metadata.get("summary") or metadata.get("description") or "",
        "version": version,
        "importedFrom": "skillhub-security-test",
    }
    files = {
        "SKILL.md": body,
        "_meta.json": json.dumps(meta, ensure_ascii=False, indent=2) + "\n",
        "skill-card.md": f"# {meta['displayName']}\n\n{meta['summary']}\n",
        ".clawhub/origin.json": json.dumps({"source": "clawhub", "importedFrom": "skillhub-security-test", "slug": slug, "version": version}, ensure_ascii=False, indent=2) + "\n",
    }
    bundle = tmpdir / f"{slug}-{version}.zip"
    manifest = []
    with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for path, content in files.items():
            data = content.encode("utf-8")
            zf.writestr(path, data)
            ctype = "text/markdown" if path.endswith(".md") else "application/json"
            manifest.append({"path": path, "size": len(data), "contentType": ctype, "sha256": hashlib.sha256(data).hexdigest()})
    total_size = sum(item["size"] for item in manifest)
    return bundle, files, manifest, total_size


def usage():
    print("Usage: ops/import-security-test-recommendations.py --apply", file=sys.stderr)
    print("Imports active recommendations from skillhub-security-test into production and marks source=clawhub.", file=sys.stderr)


def main():
    if len(sys.argv) != 2 or sys.argv[1] in {"-h", "--help"}:
        usage()
        raise SystemExit(0 if len(sys.argv) == 2 and sys.argv[1] in {"-h", "--help"} else 2)
    if sys.argv[1] != "--apply":
        usage()
        raise SystemExit(2)

    export_sql = r"""
    WITH recs AS (
      SELECT r.*, s.display_name, s.summary AS skill_summary, s.visibility, s.download_count,
             v.version, v.parsed_metadata_json, v.manifest_json,
             coalesce(jsonb_agg(ld.slug ORDER BY ld.sort_order, ld.id) FILTER (WHERE ld.slug IS NOT NULL), '[]'::jsonb) AS label_slugs
      FROM operation_recommendation r
      JOIN skill s ON s.id = r.skill_id
      JOIN skill_version v ON v.id = s.latest_version_id
      LEFT JOIN skill_label sl ON sl.skill_id = s.id
      LEFT JOIN label_definition ld ON ld.id = sl.label_id
      WHERE r.status = 'ACTIVE'
      GROUP BY r.id, s.id, v.id
      ORDER BY r.priority DESC, r.id
    )
    SELECT jsonb_pretty(jsonb_agg(to_jsonb(recs))) FROM recs;
    """
    recs = psql_json(TEST_PG, export_sql)
    labels = psql_json(TEST_PG, r"""
      SELECT jsonb_pretty(jsonb_agg(row_to_json(x)))
      FROM (
        SELECT ld.slug, ld.type, ld.visible_in_filter, ld.sort_order,
               coalesce(jsonb_object_agg(lt.locale, lt.display_name) FILTER (WHERE lt.locale IS NOT NULL), '{}'::jsonb) AS translations
        FROM label_definition ld
        LEFT JOIN label_translation lt ON lt.label_id = ld.id
        WHERE ld.slug LIKE 'domain-%'
        GROUP BY ld.id
        ORDER BY ld.sort_order, ld.id
      ) x;
    """)
    print(f"Found {len(recs)} active test recommendations")

    with tempfile.TemporaryDirectory(prefix="skillhub-import-") as td:
        tmpdir = Path(td)
        sql_parts = ["BEGIN;", f"INSERT INTO user_account (id, display_name, email, status) VALUES ({sql_literal(ACTOR)}, 'OpenClaw Rollout', 'openclaw-rollout@skillhub.local', 'ACTIVE') ON CONFLICT (id) DO NOTHING;"]
        for label in labels:
            sql_parts.append(f"""
WITH upsert_label AS (
  INSERT INTO label_definition (slug, type, visible_in_filter, sort_order, created_by)
  VALUES ({sql_literal(label['slug'])}, {sql_literal(label['type'])}, {'true' if label['visible_in_filter'] else 'false'}, {int(label['sort_order'])}, {sql_literal(ACTOR)})
  ON CONFLICT (slug) DO UPDATE SET type=EXCLUDED.type, visible_in_filter=EXCLUDED.visible_in_filter, sort_order=EXCLUDED.sort_order, updated_at=now()
  RETURNING id
)
SELECT id FROM upsert_label;
""")
            for locale, name in (label.get("translations") or {}).items():
                sql_parts.append(f"""
INSERT INTO label_translation (label_id, locale, display_name)
SELECT id, {sql_literal(locale)}, {sql_literal(name)} FROM label_definition WHERE slug={sql_literal(label['slug'])}
ON CONFLICT (label_id, locale) DO UPDATE SET display_name=EXCLUDED.display_name, updated_at=now();
""")

        bundle_copies = []
        for rec in recs:
            slug = rec["slug"]
            namespace = rec["namespace"]
            version = rec["version"]
            metadata = rec.get("parsed_metadata_json") or {}
            title = rec.get("title") or rec.get("display_name") or slug
            summary = rec.get("summary") or rec.get("skill_summary") or metadata.get("summary") or ""
            badge = normalize_badge(rec.get("badge"))
            priority = int(rec.get("priority") or 0)
            label_slugs = rec.get("label_slugs") or []
            bundle, files, manifest, total_size = build_bundle(tmpdir, slug, version, metadata)

            skill_sql = f"""
DO $$
DECLARE
  ns_id bigint;
  sid bigint;
  vid bigint;
  existing_version_id bigint;
BEGIN
  SELECT id INTO ns_id FROM namespace WHERE slug = {sql_literal(namespace)};
  IF ns_id IS NULL THEN
    INSERT INTO namespace (slug, display_name, type, description, status, created_by)
    VALUES ({sql_literal(namespace)}, {sql_literal(namespace)}, 'TEAM', {sql_literal('Imported recommendation namespace')}, 'ACTIVE', {sql_literal(ACTOR)})
    RETURNING id INTO ns_id;
  END IF;

  SELECT s.id INTO sid FROM skill s WHERE s.namespace_id = ns_id AND s.slug = {sql_literal(slug)} LIMIT 1;
  IF sid IS NULL THEN
    INSERT INTO skill (namespace_id, slug, display_name, summary, owner_id, visibility, status, download_count, star_count, rating_avg, rating_count, created_by, updated_by, hidden)
    VALUES (ns_id, {sql_literal(slug)}, {sql_literal(rec.get('display_name') or title)}, {sql_literal(rec.get('skill_summary') or summary)}, {sql_literal(OWNER)}, 'PUBLIC', 'ACTIVE', 0, 0, 0.00, 0, {sql_literal(ACTOR)}, {sql_literal(ACTOR)}, false)
    RETURNING id INTO sid;
  ELSE
    UPDATE skill SET hidden=false, status='ACTIVE', updated_by={sql_literal(ACTOR)}, updated_at=now() WHERE id=sid;
  END IF;

  SELECT id INTO existing_version_id FROM skill_version WHERE skill_id=sid AND version={sql_literal(version)} LIMIT 1;
  IF existing_version_id IS NULL THEN
    INSERT INTO skill_version (skill_id, version, status, changelog, parsed_metadata_json, manifest_json, file_count, total_size, published_at, created_by, bundle_ready, download_ready, requested_visibility)
    VALUES (sid, {sql_literal(version)}, 'PUBLISHED', {sql_literal('Imported from security test recommendations')}, {sql_literal(json.dumps(metadata, ensure_ascii=False))}::jsonb, {sql_literal(json.dumps([{k: v for k, v in item.items() if k != 'sha256'} for item in manifest], ensure_ascii=False))}::jsonb, {len(manifest)}, {total_size}, now(), {sql_literal(ACTOR)}, true, true, 'PUBLIC')
    RETURNING id INTO vid;
    UPDATE skill SET latest_version_id=vid WHERE id=sid;
    INSERT INTO skill_version_stats (skill_version_id, skill_id, download_count) VALUES (vid, sid, 0) ON CONFLICT (skill_version_id) DO NOTHING;
  ELSE
    vid := existing_version_id;
    UPDATE skill_version SET status='PUBLISHED', bundle_ready=true, download_ready=true, updated_at=created_at WHERE id=vid;
    UPDATE skill SET latest_version_id=vid WHERE id=sid AND latest_version_id IS NULL;
  END IF;

  INSERT INTO operation_recommendation (source_type, status, cache_status, skill_id, namespace, slug, title, summary, reason, badge, priority, created_by, updated_by)
  VALUES ('LOCAL_SKILL', 'ACTIVE', 'READY', sid, {sql_literal(namespace)}, {sql_literal(slug)}, {sql_literal(title)}, {sql_literal(summary)}, NULL, {sql_literal(badge)}, {priority}, {sql_literal(ACTOR)}, {sql_literal(ACTOR)})
  ON CONFLICT (skill_id) WHERE status <> 'DELETED'
  DO UPDATE SET status='ACTIVE', cache_status='READY', title=EXCLUDED.title, summary=EXCLUDED.summary, reason=NULL, badge=EXCLUDED.badge, priority=EXCLUDED.priority, updated_by={sql_literal(ACTOR)}, updated_at=now();

  INSERT INTO remote_mirror_record (
    skill_id, skill_version_id, source_registry, source_canonical_slug,
    source_namespace, source_slug, requested_version, remote_version, bundle_sha256, download_url
  )
  VALUES (
    sid, vid, 'clawhub',
    CASE WHEN {sql_literal(namespace)} = 'global' THEN {sql_literal(slug)} ELSE {sql_literal(namespace)} || '--' || {sql_literal(slug)} END,
    {sql_literal(namespace)}, {sql_literal(slug)}, {sql_literal(version)}, {sql_literal(version)}, NULL, NULL
  )
  ON CONFLICT (skill_version_id) DO UPDATE SET
    source_registry = EXCLUDED.source_registry,
    source_canonical_slug = EXCLUDED.source_canonical_slug,
    source_namespace = EXCLUDED.source_namespace,
    source_slug = EXCLUDED.source_slug,
    requested_version = EXCLUDED.requested_version,
    remote_version = EXCLUDED.remote_version;
END $$;
"""
            sql_parts.append(skill_sql)
            for label_slug in label_slugs:
                sql_parts.append(f"""
INSERT INTO skill_label (skill_id, label_id, created_by)
SELECT s.id, ld.id, {sql_literal(ACTOR)}
FROM skill s JOIN namespace n ON n.id=s.namespace_id JOIN label_definition ld ON ld.slug={sql_literal(label_slug)}
WHERE n.slug={sql_literal(namespace)} AND s.slug={sql_literal(slug)}
ON CONFLICT (skill_id, label_id) DO NOTHING;
""")
            # Copy bundle and files after SQL gives deterministic ids? We need actual prod ids, so use SQL to emit mapping later.
            bundle_copies.append({"namespace": namespace, "slug": slug, "version": version, "bundle": str(bundle), "files": files, "manifest": manifest})

        sql_parts.append("COMMIT;")
        sql = "\n".join(sql_parts)
        print("Applying database upserts...")
        run(PROD_PG, input_text=sql, capture=False)

        mapping_sql = """
        SELECT jsonb_pretty(jsonb_agg(jsonb_build_object('namespace', n.slug, 'slug', s.slug, 'skill_id', s.id, 'version', v.version, 'version_id', v.id)))
        FROM skill s JOIN namespace n ON n.id=s.namespace_id JOIN skill_version v ON v.id=s.latest_version_id
        WHERE (n.slug, s.slug) IN (%s);
        """ % ",".join(f"({sql_literal(x['namespace'])},{sql_literal(x['slug'])})" for x in bundle_copies)
        mapping = psql_json(["docker", "exec", "skillhub-postgres-1", "psql", "-U", "skillhub", "-d", PROD_DB], mapping_sql)
        by_slug = {(m['namespace'], m['slug']): m for m in mapping}

        storage_root = tmpdir / "storage"
        for item in bundle_copies:
            m = by_slug[(item['namespace'], item['slug'])]
            sid, vid = m['skill_id'], m['version_id']
            pkg_dir = storage_root / "packages" / str(sid) / str(vid)
            file_dir = storage_root / "skills" / str(sid) / str(vid)
            pkg_dir.mkdir(parents=True, exist_ok=True)
            file_dir.mkdir(parents=True, exist_ok=True)
            (pkg_dir / "bundle.zip").write_bytes(Path(item['bundle']).read_bytes())
            for rel, content in item['files'].items():
                out = file_dir / rel
                out.parent.mkdir(parents=True, exist_ok=True)
                out.write_text(content, encoding="utf-8")

        print("Copying reconstructed bundles/files into prod storage volume...")
        run(["docker", "run", "--rm", "-v", f"{PROD_VOLUME}:/storage", "-v", f"{storage_root}:/import:ro", "alpine", "sh", "-c", "cp -a /import/. /storage/"], capture=False)

        file_rows = ["BEGIN;"]
        for item in bundle_copies:
            m = by_slug[(item['namespace'], item['slug'])]
            sid, vid = m['skill_id'], m['version_id']
            for manifest_item in item['manifest']:
                rel = manifest_item['path']
                storage_key = f"skills/{sid}/{vid}/{rel}"
                file_rows.append(f"""
INSERT INTO skill_file (version_id, file_path, file_size, content_type, sha256, storage_key)
VALUES ({vid}, {sql_literal(rel)}, {manifest_item['size']}, {sql_literal(manifest_item['contentType'])}, {sql_literal(manifest_item['sha256'])}, {sql_literal(storage_key)})
ON CONFLICT (version_id, file_path) DO UPDATE SET file_size=EXCLUDED.file_size, content_type=EXCLUDED.content_type, sha256=EXCLUDED.sha256, storage_key=EXCLUDED.storage_key;
""")
        file_rows.append("COMMIT;")
        print("Upserting skill_file rows...")
        run(PROD_PG, input_text="\n".join(file_rows), capture=False)

    print("Import complete")

if __name__ == "__main__":
    main()
