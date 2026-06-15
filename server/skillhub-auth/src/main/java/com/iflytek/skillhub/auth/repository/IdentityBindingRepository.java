package com.iflytek.skillhub.auth.repository;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for links between platform users and external identity-provider subjects.
 */
@Repository
public interface IdentityBindingRepository extends JpaRepository<IdentityBinding, Long> {
    Optional<IdentityBinding> findByProviderCodeAndSubject(String providerCode, String subject);

    @Query(value = """
            select *
              from identity_binding
             where provider_code = :providerCode
               and (extra_json ->> 'corp_id' = :corpId or extra_json ->> 'corpId' = :corpId)
               and (extra_json ->> 'userid' = :userId or extra_json ->> 'userId' = :userId)
             limit 1
            """, nativeQuery = true)
    Optional<IdentityBinding> findByProviderAndCorpIdAndDingTalkUserId(
            @Param("providerCode") String providerCode,
            @Param("corpId") String corpId,
            @Param("userId") String userId);

    @Query(value = """
            select *
              from identity_binding
             where provider_code = :providerCode
               and (extra_json ->> 'corp_id' = :corpId or extra_json ->> 'corpId' = :corpId)
               and (extra_json ->> 'openid' = :openId or extra_json ->> 'openId' = :openId)
             limit 1
            """, nativeQuery = true)
    Optional<IdentityBinding> findByProviderAndCorpIdAndDingTalkOpenId(
            @Param("providerCode") String providerCode,
            @Param("corpId") String corpId,
            @Param("openId") String openId);

    List<IdentityBinding> findByUserId(String userId);
    List<IdentityBinding> findByProviderCodeAndUserIdIn(String providerCode, Collection<String> userIds);
}
