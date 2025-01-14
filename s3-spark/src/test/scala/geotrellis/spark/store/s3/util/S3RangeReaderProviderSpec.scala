/*
 * Copyright 2019 Azavea
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

package geotrellis.spark.store.s3.util

import geotrellis.spark.store.s3._
import geotrellis.store.s3.util._
import geotrellis.store.s3.S3ClientProducer
import geotrellis.util.RangeReader

import org.scalatest._

class S3RangeReaderProviderSpec extends FunSpec with Matchers {
  val client = MockS3Client.instance
  S3TestUtils.cleanBucket(client, "fake-bucket")
  S3ClientProducer.set(() => client)

  describe("S3RangeReaderProviderSpec") {
    val uri = new java.net.URI("s3://fake-bucket/some-prefix")

    it("should create a S3RangeReader from a URI") {
      val reader = RangeReader(uri)

      assert(reader.isInstanceOf[S3RangeReader])
    }
  }
}
