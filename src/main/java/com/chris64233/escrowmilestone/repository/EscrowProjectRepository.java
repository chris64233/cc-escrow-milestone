package com.chris64233.escrowmilestone.repository;

import com.chris64233.escrowmilestone.domain.EscrowProject;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EscrowProjectRepository extends JpaRepository<EscrowProject, Long> {

    /**
     * 悲观写锁读取项目，用于放款/撤销/审批撤回等需要串行化的资金操作。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from EscrowProject p where p.id = :id")
    Optional<EscrowProject> findByIdForUpdate(@Param("id") Long id);
}
