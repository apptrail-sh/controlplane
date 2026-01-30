-- Materialized view for cluster pod counts
-- Used for efficient dashboard queries
CREATE MATERIALIZED VIEW cluster_pod_counts AS
SELECT cluster_id,
       COUNT(*)                                                 as total_pods,
       COUNT(*) FILTER (WHERE status ->> 'phase' = 'Running')   as running_pods,
       COUNT(*) FILTER (WHERE status ->> 'phase' = 'Pending')   as pending_pods,
       COUNT(*) FILTER (WHERE status ->> 'phase' = 'Failed')    as failed_pods,
       COUNT(*) FILTER (WHERE status ->> 'phase' = 'Succeeded') as succeeded_pods,
       SUM(COALESCE(
               (SELECT SUM((cs ->> 'restartCount')::int)
                FROM jsonb_array_elements(status -> 'containerStatuses') cs),
               0
           ))::int                                              as total_restarts
FROM pods
WHERE deleted_at IS NULL
GROUP BY cluster_id;

CREATE UNIQUE INDEX idx_cluster_pod_counts_cluster ON cluster_pod_counts (cluster_id);

-- Materialized view for cluster node counts
CREATE MATERIALIZED VIEW cluster_node_counts AS
SELECT cluster_id,
       COUNT(*)                                                       as total_nodes,
       COUNT(*) FILTER (WHERE EXISTS (SELECT 1
                                      FROM jsonb_array_elements(status -> 'conditions') c
                                      WHERE c ->> 'type' = 'Ready'
                                        AND c ->> 'status' = 'True')) as ready_nodes,
       COUNT(*) FILTER (WHERE EXISTS (SELECT 1
                                      FROM jsonb_array_elements(status -> 'conditions') c
                                      WHERE c ->> 'type' IN ('MemoryPressure', 'DiskPressure', 'PIDPressure')
                                        AND c ->> 'status' = 'True')) as nodes_with_pressure
FROM nodes
WHERE deleted_at IS NULL
GROUP BY cluster_id;

CREATE UNIQUE INDEX idx_cluster_node_counts_cluster ON cluster_node_counts (cluster_id);

-- Comment for documentation
COMMENT ON MATERIALIZED VIEW cluster_pod_counts IS 'Aggregated pod counts per cluster, refreshed periodically';
COMMENT ON MATERIALIZED VIEW cluster_node_counts IS 'Aggregated node counts per cluster, refreshed periodically';
