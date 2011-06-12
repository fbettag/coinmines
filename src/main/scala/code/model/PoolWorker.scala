package code
package model

import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._

object PoolWorker extends PoolWorker with LongKeyedMetaMapper[PoolWorker] {
	override def dbTableName = "pool_workers"
	override def fieldOrder = List(user, name, password, active, hashrate, lasthash)
}

class PoolWorker extends LongKeyedMapper[PoolWorker] with IdPK {
	def getSingleton = PoolWorker

	object user extends MappedLongForeignKey(this, User) {
		override def dbIndexed_? = true
		override def dbNotNull_? = true
	}

	object name extends MappedString(this, 255) {
		// only allow [a-zA-Z0-9_-]
		override def dbIndexed_? = true
		override def dbNotNull_? = true
	}

	object password extends MappedString(this, 255) {
		override def dbIndexed_? = true
		override def dbNotNull_? = true
	}

	object active extends MappedBoolean(this) {
		override def dbNotNull_? = true
		override def defaultValue = false
	}

	object hashrate extends MappedInt(this)
	object lasthash extends MappedDateTime(this)
}
