package code
package model

import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._
import java.util.Date

object Share extends Share with LongKeyedMetaMapper[Share] {
	override def dbTableName = "shares"
	override def fieldOrder = List(user, remoteHost, ourResult, upstreamResult, reason, solution, timestamp)
}

class Share extends LongKeyedMapper[Share] with IdPK {
	def getSingleton = Share

	object user extends MappedLongForeignKey(this, User) {
		override def dbIndexed_? = true
		override def dbNotNull_? = true
	}

	object remoteHost extends MappedString(this, 255) {
		override def dbNotNull_? = true
	}

	object ourResult extends MappedBoolean(this) {
		override def dbNotNull_? = true
		override def defaultValue = false
	}

	object upstreamResult extends MappedBoolean(this) {
		//override def dbNotNull_? = true
		//override def defaultValue = false
	}

	object reason extends MappedString(this, 255) {
		override def dbNotNull_? = true
	}

	object solution extends MappedString(this, 1024) {
		override def dbNotNull_? = true
	}

	object timestamp extends MappedDateTime(this) {
		override def dbNotNull_? = true
		def beforeCreate = this(new Date)
	}

}
