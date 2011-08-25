package code
package model

import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._

object NetworkBlock extends NetworkBlock with LongKeyedMetaMapper[NetworkBlock] {
	override def dbTableName = "network_blocks"
	override def fieldOrder = List(blockNumber, timestamp, accountAddress, confirms)
}

class NetworkBlock extends LongKeyedMapper[NetworkBlock] with IdPK {
	def getSingleton = NetworkBlock

	object accountAddress extends MappedString(this, 255) {
		override def dbIndexed_? = true
//		override def dbNotNull_? = true
	}

	object blockNumber extends MappedInt(this) {
		override def dbNotNull_? = true
	}

	object confirms extends MappedInt(this) {
		override def dbNotNull_? = true
		override def defaultValue = 0
	}

	object timestamp extends MappedDateTime(this) with LifecycleCallbacks {
		override def dbNotNull_? = true
	}

}
