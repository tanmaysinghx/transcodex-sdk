package io.transcodex.api.storage;

import io.transcodex.core.storage.StorageException;
import java.io.InputStream;
import java.nio.file.Path;

/** Abstraction layer for asset persistence (local filesystem, S3, MinIO, etc.). */
public interface StorageProvider {

  /**
   * Stores a local file to the storage provider destination key. Implementing classes should use
   * zero-copy or channel-based I/O where possible.
   *
   * @param source the source local path to upload.
   * @param destinationKey the unique key identifying the stored object.
   * @throws StorageException if upload fails.
   */
  void store(Path source, String destinationKey) throws StorageException;

  /**
   * Stores a stream source to the storage provider.
   *
   * @param source the input stream.
   * @param destinationKey the unique key identifying the stored object.
   * @throws StorageException if upload fails.
   */
  void store(InputStream source, String destinationKey) throws StorageException;

  /**
   * Retrieves an input stream for reading the stored asset.
   *
   * @param key the unique key identifying the stored object.
   * @return the input stream to read the object.
   * @throws StorageException if retrieval fails.
   */
  InputStream retrieve(String key) throws StorageException;

  /**
   * Deletes the asset from the storage provider.
   *
   * @param key the unique key identifying the stored object.
   * @throws StorageException if deletion fails.
   */
  void delete(String key) throws StorageException;
}
