package com.chris64233.escrowmilestone.repo;

import com.chris64233.escrowmilestone.domain.EscrowProject;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EscrowProjectRepository extends JpaRepository<EscrowProject, Long> {

    Optional<EscrowProject> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * 项目行悲观写锁：同一项目上的放款 / 撤销 / 结算 / 补偿 / 审批变更均先取此锁，
     * 使并发放款、审批撤回与放款等竞争串行化，只能形成一种完整结果。
     * 实体上的 {@code @Version} 作为第二道防线。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from EscrowProject p where p.code = :code")
    Optional<EscrowProject> findByCodeForUpdate(@Param("code") String code);
}
