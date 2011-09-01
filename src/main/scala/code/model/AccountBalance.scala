/*
 *  Copyright (c) 2011, Franz Bettag <franz@bett.ag>
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * All advertising materials mentioning features or use of this software
 *       must display the following acknowledgement:
 *       This product includes software developed by the Bettag Systems UG
 *       and its contributors.
 *
 *  THIS SOFTWARE IS PROVIDED BY BETTAG SYSTEMS UG ''AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL BETTAG SYSTEMS UG BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package code
package model

import java.util.Date

import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.common._

import scala.xml._
import code.lib._

object AccountBalance extends AccountBalance with LongKeyedMetaMapper[AccountBalance] {
	override def dbTableName = "account_balances"

	private def payoutFor(network: String) = {
		var num = this.findAll(By(this.paid, true), By(this.network, network)).foldLeft(0.0) { _ + _.balance.toDouble } * (-1)
		if (num == 0.0) 0.0 else num // 0.0 == -0.0 => true
	}
	def payoutBtc = payoutFor("bitcoin")
	def payoutNmc = payoutFor("namecoin")
	def payoutSlc = payoutFor("solidcoin")
}


class AccountBalance extends LongKeyedMapper[AccountBalance] with IdPK {
	def getSingleton = AccountBalance

	object user extends MappedLongForeignKey(this, User) {
		override def dbIndexed_? = true
		override def dbNotNull_? = true
	}

	object sendAddress extends MappedString(this, 255)
	object txid extends MappedString(this, 255)

	object balance extends MappedDecimal(this, java.math.MathContext.DECIMAL64, 8) {
		override def dbNotNull_? = true
	}

	object paid extends MappedBoolean(this) {
		override def dbIndexed_? = true
		override def dbNotNull_? = true
		override def defaultValue = false
	}

	object threshold extends MappedInt(this) {
		override def dbIndexed_? = true
		override def dbNotNull_? = true
		override def defaultValue = 0
	}

	object timestamp extends MappedDateTime(this) {
		override def dbNotNull_? = true
		def beforeCreate = this(new Date)
	}

	object network extends MappedString(this, 20) {
		override def dbNotNull_? = true
		override def dbIndexed_? = true
	}

	def isEligible: Boolean = {
		if (this.paid.is)
			return true
		if (DateTimeHelpers.getDate(this.timestamp.is).isBefore(DateTimeHelpers.getDate.minusHours(12)))
			return true
		if (DateTimeHelpers.getDate(this.timestamp.is).isAfter(DateTimeHelpers.getDate.minusHours(12)) && this.threshold.is >= 2)
			return true

		false
	}

	def transactionLink: NodeSeq = {
		if (!this.paid.is) return Text(this.sendAddress.is)
		else this.network.is match {
			case "bitcoin" => <a href={"http://blockexplorer.com/tx/%s".format(this.txid.is)} target="_blank">{this.sendAddress.is}</a>
			case "namecoin" => <a href={"http://explorer.dot-big.org/tx/%s".format(this.txid.is)} target="_blank">{this.sendAddress.is}</a>
			case "solidcoin" => <a href={"http://solidcoin.whmcr.co.uk/tx/%s".format(this.txid.is)} target="_blank">{this.sendAddress.is}</a>
		}
	}

}
