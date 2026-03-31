package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.registry.RemoteMirrorRecord;
import com.iflytek.skillhub.domain.registry.RemoteMirrorRecordRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RemoteMirrorRecordJpaRepository extends JpaRepository<RemoteMirrorRecord, Long>, RemoteMirrorRecordRepository {
    @Override
    Optional<RemoteMirrorRecord> findBySkillVersionId(Long skillVersionId);
}
