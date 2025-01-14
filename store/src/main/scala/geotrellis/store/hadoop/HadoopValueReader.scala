/*
 * Copyright 2016 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.store.hadoop

import geotrellis.layer._
import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.store._
import geotrellis.store.avro._
import geotrellis.store.avro.codecs._
import geotrellis.store.hadoop.formats.FilterMapFileInputFormat

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs._
import org.apache.hadoop.io._
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import _root_.io.circe._

import scala.collection.immutable._
import scala.reflect.ClassTag

class HadoopValueReader(
  val attributeStore: AttributeStore,
  conf: Configuration,
  maxOpenFiles: Int = 16
) extends OverzoomingValueReader {

  val readers: Cache[(LayerId, Path), MapFile.Reader] =
    Scaffeine()
      .recordStats()
      .maximumSize(maxOpenFiles.toLong)
      .removalListener[(LayerId, Path), MapFile.Reader] { case (_, v, _) => v.close() }
      .build[(LayerId, Path), MapFile.Reader]

  private def predicate(row: (Path, BigInt, BigInt), index: BigInt): Boolean =
    (index >= row._2) && ((index <= row._3) || (row._3 == -1))

  def reader[K: AvroRecordCodec: Decoder: ClassTag, V: AvroRecordCodec](layerId: LayerId): Reader[K, V] = new Reader[K, V] {
    val header = attributeStore.readHeader[HadoopLayerHeader](layerId)
    val keyIndex = attributeStore.readKeyIndex[K](layerId)
    val writerSchema = attributeStore.readSchema(layerId)
    val codec = KeyValueRecordCodec[K, V]

    val ranges: Vector[(Path, BigInt, BigInt)] =
      FilterMapFileInputFormat.layerRanges(new Path(header.path), conf)

    def read(key: K): V = {
      val index: BigInt = keyIndex.toIndex(key)
      val valueWritable: BytesWritable =
      ranges
        .find(row => predicate(row, index))
        .map { case (path, _, _) =>
          readers.get((layerId, path), _ => new MapFile.Reader(path, conf))
        }
        .getOrElse(throw new ValueNotFoundError(key, layerId))
          .get(new BigIntWritable(index.toByteArray), new BytesWritable())
          .asInstanceOf[BytesWritable]

      if (valueWritable == null) throw new ValueNotFoundError(key, layerId)
      AvroEncoder
        .fromBinary(writerSchema, valueWritable.getBytes)(codec)
        .find { row => row._1 == key }
        .getOrElse(throw new ValueNotFoundError(key, layerId))
        ._2
    }
  }
}

object HadoopValueReader {
  def apply(attributeStore: HadoopAttributeStore): HadoopValueReader =
    new HadoopValueReader(attributeStore, attributeStore.conf)

  def apply(rootPath: Path, conf: Configuration): HadoopValueReader =
    apply(HadoopAttributeStore(rootPath, conf))
}
