package dev.agiro.fanel.household.infra;

import dev.agiro.fanel.household.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    List<Member> findAllByHouseholdId(UUID householdId);
    Optional<Member> findByUsername(String username);
    Optional<Member> findByOidcSubject(String oidcSubject);

    @Modifying
    @Query(value = "DELETE FROM member_guardian WHERE child_id = :memberId OR guardian_id = :memberId", nativeQuery = true)
    void deleteGuardianLinks(@Param("memberId") String memberId);
}
