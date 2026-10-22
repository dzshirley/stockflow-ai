package com.stockflow.analysis;

import com.stockflow.analysis.BusinessAnalysisModels.AnalysisType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface BusinessReportRepository extends JpaRepository<BusinessReportEntity, UUID> {

    Optional<BusinessReportEntity> findByStoreIdAndCreatedByUserIdAndIdempotencyKey(
            UUID storeId, UUID createdByUserId, String idempotencyKey);

    Optional<BusinessReportEntity> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<BusinessReportEntity> findByIdAndStoreIdAndCreatedByUserId(
            UUID id, UUID storeId, UUID createdByUserId);

    Page<BusinessReportEntity> findByStoreIdOrderByCreatedAtDescIdDesc(UUID storeId, Pageable pageable);

    Page<BusinessReportEntity> findByStoreIdAndCreatedByUserIdOrderByCreatedAtDescIdDesc(
            UUID storeId, UUID createdByUserId, Pageable pageable);

    Page<BusinessReportEntity> findByStoreIdAndStatusOrderByCreatedAtDescIdDesc(
            UUID storeId, BusinessAnalysisReport.Status status, Pageable pageable);

    Page<BusinessReportEntity> findByStoreIdAndCreatedByUserIdAndStatusOrderByCreatedAtDescIdDesc(
            UUID storeId, UUID createdByUserId, BusinessAnalysisReport.Status status, Pageable pageable);

    Page<BusinessReportEntity> findByStoreIdAndAnalysisTypeOrderByCreatedAtDescIdDesc(
            UUID storeId, AnalysisType type, Pageable pageable);

    Page<BusinessReportEntity> findByStoreIdAndCreatedByUserIdAndAnalysisTypeOrderByCreatedAtDescIdDesc(
            UUID storeId, UUID createdByUserId, AnalysisType type, Pageable pageable);

    Page<BusinessReportEntity> findByStoreIdAndStatusAndAnalysisTypeOrderByCreatedAtDescIdDesc(
            UUID storeId, BusinessAnalysisReport.Status status, AnalysisType type, Pageable pageable);

    Page<BusinessReportEntity> findByStoreIdAndCreatedByUserIdAndStatusAndAnalysisTypeOrderByCreatedAtDescIdDesc(
            UUID storeId,
            UUID createdByUserId,
            BusinessAnalysisReport.Status status,
            AnalysisType type,
            Pageable pageable);
}
