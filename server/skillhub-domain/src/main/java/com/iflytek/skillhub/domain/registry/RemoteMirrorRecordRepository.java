package com.iflytek.skillhub.domain.registry;

import java.util.Optional;

public interface RemoteMirrorRecordRepository {
    RemoteMirrorRecord save(RemoteMirrorRecord record);

    Optional<RemoteMirrorRecord> findBySkillVersionId(Long skillVersionId);
}
