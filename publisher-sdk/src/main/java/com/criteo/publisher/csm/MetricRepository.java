package com.criteo.publisher.csm;

import android.support.annotation.NonNull;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

class MetricRepository {

  private static final String TAG = MetricRepository.class.getSimpleName();

  @NonNull
  private final MetricDirectory directory;

  @NonNull
  private final ConcurrentMap<File, SyncMetricFile> metricFileById = new ConcurrentHashMap<>();

  MetricRepository(@NonNull MetricDirectory directory) {
    this.directory = directory;
  }

  /**
   * Atomically update the metric matching the given id with the given updater.
   * <p>
   * If no metric match the given id, then a new empty one is created and updated accordingly.
   * <p>
   * When multiple threads attempt updates, the update function may be called multiple times.
   *
   * @param impressionId ID of the metric to update
   * @param updater function representing the update to apply
   */
  void updateById(@NonNull String impressionId, @NonNull MetricUpdater updater) {
    File metricFile = directory.createMetricFile(impressionId);
    SyncMetricFile syncMetricFile = getOrCreateMetricFile(metricFile);

    try {
      syncMetricFile.update(updater);
    } catch (IOException e) {
      Log.d(TAG, "Error while updating metric", e);
    }
  }

  /**
   * Returns all stored metric into this repository.
   * <p>
   * Individual metrics are read atomically, however the overall metrics are not read at the same
   * time and hence is not globally atomic.
   *
   * @return all stored metrics
   */
  @NonNull
  Collection<Metric> getAllStoredMetrics() {
    Collection<File> files = directory.listFiles();

    List<Metric> metrics = new ArrayList<>(files.size());
    for (File metricFile : files) {
      try {
        Metric metric = getOrCreateMetricFile(metricFile).read();
        metrics.add(metric);
      } catch (IOException e) {
        Log.d(TAG, "Error while reading metric", e);
      }
    }
    return metrics;
  }

  /**
   * Atomically get or create a synchronized metric file on the given file.
   * <p>
   * At most, one {@link SyncMetricFile} should exist for an underlying file. This method
   * ensure this with a logic similar to {@link ConcurrentMap#computeIfAbsent(Object, Function)}.
   * Note that this method is not directly used because it requires Android API level >= 24.
   *
   * @param metricFile underlying file to synchronized
   * @return unique instance of synchronized and atomic file over given one
   */
  private SyncMetricFile getOrCreateMetricFile(@NonNull File metricFile) {
    SyncMetricFile oldMetric = metricFileById.get(metricFile);
    if (oldMetric == null) {
      SyncMetricFile newMetric = directory.createSyncMetricFile(metricFile);
      oldMetric = (oldMetric = metricFileById.putIfAbsent(metricFile, newMetric)) == null
          ? newMetric : oldMetric;
    }
    return oldMetric;
  }

  interface MetricUpdater {

    void update(@NonNull Metric.Builder metricBuilder);
  }

}