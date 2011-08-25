package code
package model

import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._

object PoolWorker extends PoolWorker with LongKeyedMetaMapper[PoolWorker] {
	override def dbTableName = "pool_worker"
}

class PoolWorker extends LongKeyedMapper[PoolWorker] with IdPK {
	def getSingleton = PoolWorker

	object user extends MappedLongForeignKey(this, User) {
		override def dbIndexed_? = true
		override def dbNotNull_? = true
	}

	object username extends MappedString(this, 255) {
		override def dbIndexed_? = true
		override def dbNotNull_? = true
	}

	object password extends MappedString(this, 255) {
		override def dbIndexed_? = true
		override def dbNotNull_? = true
	}

	object hashrate extends MappedInt(this)
	object lasthash extends MappedDateTime(this)
}
