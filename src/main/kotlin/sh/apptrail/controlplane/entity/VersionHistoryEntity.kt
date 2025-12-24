package sh.apptrail.controlplane.database

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "version_history")
class VersionHistoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "workload_name", nullable = false)
    var workloadName: String = "",

    @Column(name = "cluster_name", nullable = false)
    var clusterName: String = "",

    @Column(name = "version", nullable = false)
    var version: String = "",

    @Column(name = "observed_at", nullable = false)
    var observedAt: Instant = Instant.EPOCH,
)
