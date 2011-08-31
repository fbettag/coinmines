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
package comet

import net.liftweb.http._
import net.liftweb.actor._
import net.liftweb.http.S._
import net.liftweb.http.SHtml._
import net.liftweb.http.js.JsCmds._
import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.common._

import org.joda.time._
import java.util.Date
import java.math.BigInteger

import scala.xml._
import scala.collection.mutable.Map

import lib._
import model._

case object Tick

sealed trait StatsGatherCommand
case class StatsCleanup extends StatsGatherCommand
case class StatsReward(repeat: Boolean) extends StatsGatherCommand
case class StatsGatherGlobal(target: CometActor) extends StatsGatherCommand
case class StatsGatherUser(target: CometActor, user: User) extends StatsGatherCommand

sealed trait StatsReply
case class StatsUserCoinReply(hashrate: Int, total: Long, round: Long, stale: Long, reward: Double, rewardUnconfirmed: Double) extends StatsReply
case class StatsUserReply(
	user: User, lastUpdate: DateTime,
	hashrate: Int, total: Long, stale: Long,
	bitcoin: StatsUserCoinReply, namecoin: StatsUserCoinReply, solidcoin: StatsUserCoinReply,
	global: StatsGlobalReply) extends StatsReply

case class StatsGlobalCoinReply(hashrate: Int, shares: Long, payout: Double) extends StatsReply
case class StatsGlobalReply(
	lastUpdate: DateTime,
	hashrate: Int, workers: Int,
	bitcoin: StatsGlobalCoinReply, namecoin: StatsGlobalCoinReply, solidcoin: StatsGlobalCoinReply) extends StatsReply


object StatCollector extends LiftActor {

	def boot() {
	}

	val calculateRewards = Props.get("pool.calculate") match {
			case Full(a: String) => a.toBoolean
			case _ => false
	}

	if (calculateRewards)
	ActorPing.schedule(this, StatsReward(true), 1 second)
	
	ActorPing.schedule(this, Tick, 1 second)
	ActorPing.schedule(this, StatsCleanup, 5 minutes)

	protected def messageHandler = {
		case a: StatsGatherGlobal =>
			a.target ! getGlobal
		case a: StatsGatherUser =>
			a.target ! getUser(a.user)
		case a: StatsCleanup =>
			cleanupJob
			ActorPing.schedule(this, a, 10 minute)
		case a: StatsReward =>
			rewardJob
			if (a.repeat) ActorPing.schedule(this, a, 10 seconds)
		case Tick =>
			minuteJob
			ActorPing.schedule(this, Tick, 1 minute)
		case _ =>
	}

	def currentDate = DateTimeHelpers.getDate
	def invalidDate = currentDate.minusMinutes(1).minusSeconds(1)
	def shortInvalidDate = currentDate.minusSeconds(30)
	def transactionFee = 0.02

	private var globalReply =
		StatsGlobalReply(invalidDate, 0, 0, StatsGlobalCoinReply(0, 0, 0.0), StatsGlobalCoinReply(0, 0, 0.0), StatsGlobalCoinReply(0, 0, 0.0))
	private var userReplies: Map[String, StatsUserReply] = Map()
	
	def balanceIsEligible(u: User, b: AccountBalance): Boolean = {
		if (DateTimeHelpers.getDate(b.timestamp).isBefore(currentDate.minusHours(24)))
			return true
		
		if (DateTimeHelpers.getDate(b.timestamp).isAfter(currentDate.minusHours(24)) && u.donatePercent >= 2.0)
			return true

		false
	}

	private def accountBalance(user: User, network: String) = {
		val balances = network match {
			case "bitcoin" => user.balances_btc
			case "namecoin" => user.balances_nmc
			case "solidcoin" => user.balances_slc
		}

		balances.filter(!balanceIsEligible(user, _)).foldLeft(0.0) { _ + _.balance.toDouble } - transactionFee
	}

	private def unconfirmedReward(user: User, network: String) = {
		val balances = network match {
			case "bitcoin" => user.balances_btc
			case "namecoin" => user.balances_nmc
			case "solidcoin" => user.balances_slc
		}

		balances.filter(balanceIsEligible(user, _)).foldLeft(0.0) { _ + _.balance.toDouble } - transactionFee
	}

	private def reward(donate: Double, network: String, current: Long, total: Long) = {
		val coins = network match {
			case "bitcoin" => 50.00
			case "namecoin" => 50.00
			case "solidcoin" => 32.00
			case _ => 0.0
		}

		val calcTotal = total
		val poolfee = try { Props.get("pool.fee").openOr(0).toString.toDouble } catch { case _ => 0.0 }

		((coins.toDouble * (1.0 - (poolfee.toDouble / 100.0)) / calcTotal.toDouble) * current.toDouble) * (1-(donate.toDouble/100.0)) - transactionFee
	}


	/** Jobs **/
	private def cleanupJob {
		userReplies.map(r => if (r._2.lastUpdate.isBefore(currentDate.minusMinutes(30))) userReplies -= r._1)
	}

	private def rewardJob {
		def calcCoins(network: String) {
			// Find all winning shares and sort them by ID
			Share.findAll(By(Share.upstreamResult, true), By(Share.network, network), OrderBy(Share.id, Ascending)).map(winner => {
				if (WonShare.count(By(WonShare.id, winner.id.is)) > 0) return
				val shareCount = Share.count(By(Share.network, winner.network), By_<(Share.id, winner.id.is))
				val staleCount = Share.count(By(Share.network, winner.network), By_<(Share.id, winner.id.is), By(Share.ourResult, false))

				val block = Coind.run(network match {
					case "bitcoin" => BtcCmd("getblocknumber")
					case "namecoin" => NmcCmd("getblocknumber")
					case "solidcoin" => SlcCmd("getblocknumber")
				}).toLong - 1L

				try {
					println("Found winning share %s".format(winner.id.is))
					// Find all workers which submitted shares in this network before our winning block
					val stalesQueryString = "SELECT count(id) FROM shares AS ss WHERE id < %s AND network = '%s' AND our_result = false AND ss.username = ws.username GROUP BY username".format(winner.id.is, winner.network.is)
					val sharesQueryString = "SELECT username, count(id) AS shares, (%s) AS stales FROM shares AS ws WHERE id < %s AND network = '%s' GROUP BY username".format(stalesQueryString, winner.id.is, winner.network.is)
					println("query: %s".format(sharesQueryString))
					val r = DB.runQuery(sharesQueryString)


					// for every worker with shares, try to find it
					r._2.map(wi => PoolWorker.find(By(PoolWorker.username, wi(0))) match {
						case Full(p: PoolWorker) => archiveShares(p, winner.network.is, wi(1).toLong, wi(2).toLong, winner.id.is)
						case _ =>
					})

					def archiveShares(worker: PoolWorker, network: String, shares: Long, stales: Long, belowId: Long) {
						println("got %s shares (%s stale) for %s on %s (below %s)".format(shares, stales, worker.username, network, belowId))
						val user = worker.owner.reload

						// shares
						user.shares_total(user.shares_total.is + shares)
						user.shares_stale(user.shares_stale.is + stales)
						network match {
							case "bitcoin" =>
								user.shares_total_btc(user.shares_total_btc.is + shares)
								user.shares_stale_btc(user.shares_stale_btc.is + stales)

							case "namecoin" =>
								user.shares_total_nmc(user.shares_total_nmc.is + shares)
								user.shares_stale_nmc(user.shares_stale_nmc.is + stales)

							case "solidcoin" =>
								user.shares_total_slc(user.shares_total_slc.is + shares)
								user.shares_stale_slc(user.shares_stale_slc.is + stales)

							case _ =>
						}

						// reward
						val rew = reward(user.donatePercent.toDouble, network, shares, shareCount)
						AccountBalance.create.user(user.id.is).network(network).balance(rew).save
						val totalBalance = accountBalance(user, network)
						network match {
							case "bitcoin" => user.balance_btc(totalBalance)
							case "namecoin" => user.balance_nmc(totalBalance)
							case "solidcoin" => user.balance_slc(totalBalance)
						}

						user.save
					}
				 } catch { case _ => }

				WonShare.create.id(winner.id.is).
					blockNumber(block).
					username(winner.username.is).
					ourResult(winner.ourResult.is).
					upstreamResult(winner.upstreamResult.is).
					reason(winner.reason.is).
					solution(winner.solution.is).
					timestamp(winner.timestamp.is).
					source(winner.source.is).
					network(winner.network.is).
					shares(shareCount).
					stales(staleCount).
					save
				Share.bulkDelete_!!(By_<(Share.id, winner.id.is), By(Share.network, network))
				winner.delete_!
			})
		}

		calcCoins("bitcoin")
		calcCoins("namecoin")
		calcCoins("solidcoin")
	}

	private def minuteJob {
		try {
			// We do this in database as row-based update, seemed faster than to iterate over everyone in scala.
			DB.runQuery("""
				UPDATE pool_worker SET
				lasthash = (SELECT MAX(shares.timestamp_c) FROM shares WHERE pool_worker.username = shares.username),
				hashrate = (SELECT (COUNT(id) * 4294967296)/600/1000000 FROM shares WHERE shares.timestamp_c >= NOW() - interval '10 minutes' AND shares.username = pool_worker.username),
				hashrate_btc = (SELECT (COUNT(id) * 4294967296)/600/1000000 FROM shares WHERE shares.timestamp_c >= NOW() - interval '10 minutes' AND shares.username = pool_worker.username AND shares.network = 'bitcoin'),
				hashrate_nmc = (SELECT (COUNT(id) * 4294967296)/600/1000000 FROM shares WHERE shares.timestamp_c >= NOW() - interval '10 minutes' AND shares.username = pool_worker.username AND shares.network = 'namecoin'),
				hashrate_slc = (SELECT (COUNT(id) * 4294967296)/600/1000000 FROM shares WHERE shares.timestamp_c >= NOW() - interval '10 minutes' AND shares.username = pool_worker.username AND shares.network = 'solidcoin')
				RETURNING 0""")
		} catch { case _ => }

		def addBlock(net: String, diff: Double, block: Long): Boolean =
			NetworkBlock.find(By(NetworkBlock.network, net), By(NetworkBlock.blockNumber, block)) match {
				case Full(a: NetworkBlock) => true
				case _ => NetworkBlock.create.blockNumber(block).timestamp(currentDate.toDate).network(net).difficulty(diff).save
			}


		/* Update blocks */
		var diff = Coind.run(BtcCmd("getdifficulty")).toDouble
		var block = Coind.run(BtcCmd("getblocknumber")).toLong
		addBlock("bitcoin", diff, block)
		
		diff = Coind.run(NmcCmd("getdifficulty")).toDouble
		block = Coind.run(NmcCmd("getblocknumber")).toLong
		addBlock("namecoin", diff, block)
		
		diff = Coind.run(SlcCmd("getdifficulty")).toDouble
		block = Coind.run(SlcCmd("getblocknumber")).toLong
		addBlock("solidcoin", diff, block)
	}

	/* reload needed data */
	private def getUser(user: User): StatsUserReply = {
	
		def shareQuery(network: String) =
			Share.count(By(Share.network, network), Like(Share.username, "%s_%%".format(user.email.is)))

		def staleQuery(network: String) =
			Share.count(By(Share.network, network), Like(Share.username, "%s_%%".format(user.email.is)), By(Share.ourResult, false))

		userReplies.get(user.email) match {
			case Some(a: StatsUserReply) if (a.lastUpdate.isAfter(invalidDate)) => a
			case _ => {
				val btcShares = shareQuery("bitcoin")
				val nmcShares = shareQuery("namecoin")
				val slcShares = shareQuery("solidcoin")

				val btcStales = staleQuery("bitcoin")
				val nmcStales = staleQuery("namecoin")
				val slcStales = staleQuery("solidcoin")

				val repl: StatsUserReply = new StatsUserReply(user, currentDate,
					user.workers.foldLeft(0) { _ + _.hashrate },
					user.shares_total.is + btcShares + nmcShares + slcShares,
					user.shares_stale.is + btcStales + nmcStales + slcStales,
					StatsUserCoinReply(
						user.workers.foldLeft(0) { _ + _.hashrate_btc },
						user.shares_total_btc.is + btcShares, btcShares, user.shares_stale_btc.is + btcStales,
						reward(user.donatePercent.toDouble, "bitcoin", btcShares, getGlobal.bitcoin.shares),
						unconfirmedReward(user, "bitcoin")),
					StatsUserCoinReply(
						user.workers.foldLeft(0) { _ + _.hashrate_nmc },
						user.shares_total_nmc.is + nmcShares, nmcShares, user.shares_stale_nmc.is + nmcStales,
						reward(user.donatePercent.toDouble, "namecoin", nmcShares, getGlobal.namecoin.shares),
						unconfirmedReward(user, "namecoin")),
					StatsUserCoinReply(
						user.workers.foldLeft(0) { _ + _.hashrate_slc },
						user.shares_total_slc.is + slcShares, slcShares, user.shares_stale_slc.is + slcStales,
						reward(user.donatePercent.toDouble, "solidcoin", slcShares, getGlobal.solidcoin.shares),
						unconfirmedReward(user, "solidcoin")),
					getGlobal)
				userReplies += (user.email.is -> repl)
				repl
			}
		}
	}

	private def getGlobal: StatsGlobalReply = {
		if (globalReply.lastUpdate.isBefore(shortInvalidDate)) {
			/* Shares */
			def sharesQuery(network: String) = Share.count(By(Share.network, network))

			val globalSharesBtc = sharesQuery("bitcoin")
			val globalSharesNmc = sharesQuery("namecoin")
			val globalSharesSlc = sharesQuery("solidcoin")

			/* Hashrate */
			def hashrateQuery(network: String) = {
				val shares = Share.count(By_>(Share.timestamp, currentDate.minusMinutes(10).toDate), By(Share.network, network))
				BigInt(shares) * BigInt("4294967296") / 600 / 1000000
			}.toInt

			val globalHashrateBtc = hashrateQuery("bitcoin")
			val globalHashrateNmc = hashrateQuery("namecoin")
			val globalHashrateSlc = hashrateQuery("solidcoin")
			val globalHashrate = globalHashrateBtc + globalHashrateNmc + globalHashrateSlc

			/* Workers */
			val globalWorkers = PoolWorker.count(By_>(PoolWorker.lasthash, currentDate.minusMinutes(10).toDate)).toInt

			globalReply = StatsGlobalReply(currentDate, globalHashrate, globalWorkers,
				StatsGlobalCoinReply(globalHashrateBtc, globalSharesBtc, 0.0), // globalPayoutBtc
				StatsGlobalCoinReply(globalHashrateNmc, globalSharesNmc, 0.0), // globalPayoutNmc
				StatsGlobalCoinReply(globalHashrateSlc, globalSharesSlc, 0.0)) // globalPayoutSlc
		}
		globalReply
	}

}

class StatComet extends CometActor {
	override def defaultPrefix = Full("stat")
  
	// Redraw every Minute
	ActorPing.schedule(this, Tick, 1 second)

	// If this actor is not used for 2 minutes, destroy it
	override def lifespan: Box[TimeSpan] = Full(2 minutes)

	override def lowPriority = {
		case Tick =>
			val msg: StatsGatherCommand = User.currentUser match {
				case Full(u: User) => StatsGatherUser(this, u)
				case _ => StatsGatherGlobal(this)
			}
			StatCollector ! msg

		case a: StatsGlobalReply =>
			partialUpdate(Replace("thestats", cssSel(a).apply(defaultHtml)))
			ActorPing.schedule(this, Tick, 100 seconds) 

		case a: StatsUserReply =>
			partialUpdate(Replace("thestats", cssSel(a).apply(defaultHtml)))
			ActorPing.schedule(this, Tick, 30 seconds) 
	}

	def render = {
		this ! Tick
		<span id="thestats"/>
	}

	def hashrate = 0
	def cssSel(r: StatsUserReply): CssSel =
		"#workerlist [style]" #> (if (r.hashrate > 0.000) "" else "display: none") &
		".miner_row *" #> r.user.reload.workers.map(w => {
			w.reload
			".miner_name *" #> w.username &
			".miner_hashrate *" #> w.hashrate &
			//".miner_hashrate_btc *" #> "%s MH/s".format(w.hashrate_btc) &
			//".miner_hashrate_nmc *" #> "%s MH/s".format(w.hashrate_nmc) &
			//".miner_hashrate_slc *" #> "%s MH/s".format(w.hashrate_slc) &
			".miner_lasthash *" #> w.lasthash.toString 
		}) &
		".user_hashrate *" #> "%s MH/sec".format(r.hashrate) &
		".user_shares_total *" #> r.total &
		".user_shares_stale *" #> r.stale &
		".user_btc_hashrate *" #> "%s MH/sec".format(r.bitcoin.hashrate)  &
		".user_btc_shares_total *" #> r.bitcoin.total &
		".user_btc_shares_round *" #> r.bitcoin.round &
		".user_btc_shares_stale *" #> r.bitcoin.stale &
		".user_btc_balance *" #> "%.8f BTC".format(r.user.balance_btc.is) &
		".user_btc_reward *" #> "%.8f BTC".format(r.bitcoin.reward) &
		".user_btc_reward_unconfirmed *" #> "%.8f BTC".format(r.bitcoin.rewardUnconfirmed) &
		".user_btc_payout *" #> "%.8f BTC".format(0.toFloat) &
		".user_nmc_hashrate *" #> "%s MH/sec".format(r.namecoin.hashrate)  &
		".user_nmc_shares_total *" #> r.namecoin.total &
		".user_nmc_shares_round *" #> r.namecoin.round &
		".user_nmc_shares_stale *" #> r.namecoin.stale &
		".user_nmc_balance *" #> "%.8f NMC".format(r.user.balance_nmc.is) &
		".user_nmc_reward *" #> "%.8f NMC".format(r.namecoin.reward) &
		".user_nmc_reward_unconfirmed *" #> "%.8f NMC".format(r.namecoin.rewardUnconfirmed) &
		".user_nmc_payout *" #> "%.8f NMC".format(0.toFloat) &
		".user_slc_hashrate *" #> "%s MH/sec".format(r.solidcoin.hashrate)  &
		".user_slc_shares_total *" #> r.solidcoin.total &
		".user_slc_shares_round *" #> r.solidcoin.round &
		".user_slc_shares_stale *" #> r.solidcoin.stale &
		".user_slc_balance *" #> "%.8f SLC".format(r.user.balance_slc.is) &
		".user_slc_reward *" #> "%.8f SLC".format(r.solidcoin.reward) &
		".user_slc_reward_unconfirmed *" #> "%.8f SLC".format(r.solidcoin.rewardUnconfirmed) &
		".user_slc_payout *" #> "%.8f SLC".format(0.toFloat) &
		cssSel(r.global)

	def cssSel(r: StatsGlobalReply): CssSel =
		".last_updated *" #> <xml:group>Last updated at<br/>{DateTimeHelpers.getDate.toString("yyyy-MM-dd HH:mm:ss")}</xml:group> &
		".global_hashrate *" #> "%.1f GH/sec".format(r.hashrate / 1000.0)  &
		".global_workers *" #> r.workers &
		".global_btc_hashrate *" #> "%.3f GH/sec".format(r.bitcoin.hashrate / 1000.0)  &
		".global_btc_shares *" #> r.bitcoin.shares &
		".global_btc_payout *" #> "%.8f BTC".format(0.toFloat) &
		".global_nmc_hashrate *" #> "%.3f GH/sec".format(r.namecoin.hashrate / 1000.0)  &
		".global_nmc_shares *" #> r.namecoin.shares &
		".global_nmc_payout *" #> "%.8f NMC".format(0.toFloat) &
		".global_slc_hashrate *" #> "%.3f GH/sec".format(r.solidcoin.hashrate / 1000.0)  &
		".global_slc_shares *" #> r.solidcoin.shares &
		".global_slc_payout *" #> "%.8f SLC".format(0.toFloat) &
		".blocks_row *" #> WonShare.findAll(OrderBy(WonShare.timestamp, Descending), MaxRows(20)).map(s =>
			".blocks_network *" #> s.network &
			".blocks_id *" #> s.blockNumber.toString &
			".blocks_time *" #> s.timestamp.toString &
			".blocks_shares *" #> s.shares.toString
		)

}

