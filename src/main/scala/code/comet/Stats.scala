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
import net.liftweb.http.js._
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.js.JsCmd
import net.liftweb.http.SHtml._
import net.liftweb.mapper._
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.common._

import org.joda.time._
import java.util.Date
import java.math.BigInteger

import scala.xml._
import scala.collection.mutable.Map
import scala.collection.immutable.HashMap

import lib._
import model._

case object Tick

sealed trait StatsGatherCommand
case class StatsCleanup(calculate: Boolean) extends StatsGatherCommand
case class StatsGatherGlobal(target: CometActor) extends StatsGatherCommand
case class StatsGatherUser(target: CometActor, user: User) extends StatsGatherCommand

sealed trait StatsReply
case class StatsUserCoinReply(hashrate: Int, total: Long, round: Long, stale: Long,
	reward: Double, rewardUnconfirmed: Double, totalPayout: Double) extends StatsReply
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

	this ! Tick
	this ! StatsCleanup(Props.get("pool.calculate") match {
		case Full(a: String) => a.toBoolean
		case _ => false
	})

	protected def messageHandler = {
		case a: StatsGatherGlobal =>
			a.target ! getGlobal
		case a: StatsGatherUser =>
			a.target ! getUser(a.user)
		case a: StatsCleanup =>
			cleanupJob(a.calculate)
			//ActorPing.schedule(this, a, 5 minutes)
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
	

	/** Jobs **/
	private def cleanupJob(doCalcuations: Boolean) {

		def parseTransactions(network: String) {
			println("")
			println("parsing transactions for: %s".format(network))
			println("")
			val cmd = network match {
				case "bitcoin" => BtcCmd("listtransactions")
				case "namecoin" => NmcCmd("listtransactions")
				case "solidcoin" => SlcCmd("listtransactions")
			}

			Coind.parse(Coind.run(cmd)) match {
				case a: List[HashMap[String, Any]] =>
					a.map(d => d).
					filter(d => d.get("category").get.toString.matches("generate|immature|orphan")).
					map(t => {
						val confirmations = t.get("confirmations").getOrElse(0).toString.toLong
						val txid = t.get("txid").get.toString
						val category = t.get("category").get.toString

						WonShare.find(By(WonShare.txid, txid), By(WonShare.network, network)) match {

							// Update confirmations and category on found blocks
							case Full(ws: WonShare) =>
								println("updating existing share! %s %s".format(ws.id.is, category))
								ws.confirmations(confirmations).category(category).save

							// Find Won Shares which don't have a tx id
							case _ =>
								WonShare.find(By(WonShare.txid, ""), By(WonShare.network, network),
									OrderBy(WonShare.blockNumber, Ascending)) match {
										case Full(ws: WonShare) =>
											ws.confirmations(confirmations).category(category).txid(txid).save
											case _ => println("could not find a valid block for this!")
									}
						}	
					})

				case _ => println("not the expected list")
			}
		}

		def calcCoins(winner: WonShare): Boolean = {
			if (winner.paid.is) return true
			try {
				// Find all workers which submitted shares in this network before our winning block
				val stalesQueryString = "SELECT count(id) FROM shares AS ss WHERE id < %s AND network = '%s' AND our_result = false AND ss.username = ws.username GROUP BY username".format(winner.id.is, winner.network.is)
				val sharesQueryString = "SELECT username, count(id) AS shares, (%s) AS stales FROM shares AS ws WHERE id < %s AND network = '%s' GROUP BY username".format(stalesQueryString, winner.id.is, winner.network.is)
				val r = DB.runQuery(sharesQueryString)

				// for every worker with shares, try to find it
				r._2.map(wi => PoolWorker.find(By(PoolWorker.username, wi(0))) match {
					case Full(p: PoolWorker) => archiveShares(p, winner.network.is, wi(1).toLong, wi(2).toLong, winner.id.is)
					case _ =>
				})

				def archiveShares(worker: PoolWorker, network: String, shares: Long, stales: Long, belowId: Long) {
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
					val rew = user.reward(network, shares, winner.shares.is)
					AccountBalance.create.
						user(user.id.is).
						network(network).
						balance(rew).
						timestamp(currentDate.toDate).
						threshold(user.donatePercent.is.toDouble.floor.toInt).
						paid(false).save
		
					user.save
				}
			 } catch { case _ => }

			 winner.paid(true).save
		}

		if (doCalcuations) {

			// Find all winning shares and sort them by ID
			Share.findAll(By(Share.upstreamResult, true), OrderBy(Share.id, Ascending)).map(winner =>
				WonShare.find(By(WonShare.id, winner.id.is), By(WonShare.network, winner.network.is)) match {
					case Full(a: WonShare) => println("already found WonShare for %s %s".format(winner.network.is, winner.id.is))
					case _ =>
						val shareCount = Share.count(By(Share.network, winner.network.is), By_<(Share.id, winner.id.is))
						val staleCount = Share.count(By(Share.network, winner.network.is), By_<(Share.id, winner.id.is), By(Share.ourResult, false))
						val ws = WonShare.create.id(winner.id.is).
							username(winner.username.is).
							ourResult(winner.ourResult.is).
							upstreamResult(winner.upstreamResult.is).
							reason(winner.reason.is).
							solution(winner.solution.is).
							timestamp(winner.timestamp.is).
							source(winner.source.is).
							network(winner.network.is).
							shares(shareCount).
							stales(staleCount)
						println("valid? %s\tsaved?\t".format(ws.validate, ws.save))
						parseTransactions(winner.network.is)
				}
			)

			parseTransactions("bitcoin")
			parseTransactions("namecoin")
			parseTransactions("solidcoin")
			
			WonShare.findAll(By(WonShare.blockNumber, 0L), NotBy(WonShare.txid, "")).map(ws => ws.fetchInfo)

			// Find all WonShares **WITH** a transactionid and confirmation-count > 10 and split the share
			WonShare.findAll(By(WonShare.paid, false), NotBy(WonShare.txid, ""), By_>(WonShare.confirmations, 10), OrderBy(WonShare.id, Ascending)).map(wonShare => {
				if (calcCoins(wonShare))
					Share.bulkDelete_!!(By_<(Share.id, wonShare.id.is + 1L), By(Share.network, wonShare.network.is))
			})
		}

		// Recalculate balance
		User.findAll.map(u => u.balance_btc(u.balanceBtcDB).balance_nmc(u.balanceNmcDB).balance_slc(u.balanceSlcDB).save)

		// Cleanup userReplies hash
		userReplies.map(r => if (r._2.lastUpdate.isBefore(currentDate.minusMinutes(30))) userReplies -= r._1)
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
			Share.count(By(Share.network, network), Like(Share.username, "%s_%%".format(user.name.is)))

		def staleQuery(network: String) =
			Share.count(By(Share.network, network), Like(Share.username, "%s_%%".format(user.name.is)), By(Share.ourResult, false))

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
						user.rewardBtc(btcShares, getGlobal.bitcoin.shares),
						user.unconfirmedBtc,
						user.payoutBtc),
					StatsUserCoinReply(
						user.workers.foldLeft(0) { _ + _.hashrate_nmc },
						user.shares_total_nmc.is + nmcShares, nmcShares, user.shares_stale_nmc.is + nmcStales,
						user.rewardNmc(nmcShares, getGlobal.namecoin.shares),
						user.unconfirmedNmc,
						user.payoutNmc),
					StatsUserCoinReply(
						user.workers.foldLeft(0) { _ + _.hashrate_slc },
						user.shares_total_slc.is + slcShares, slcShares, user.shares_stale_slc.is + slcStales,
						user.rewardSlc(slcShares, getGlobal.solidcoin.shares),
						user.unconfirmedSlc,
						user.payoutSlc),
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
				StatsGlobalCoinReply(globalHashrateBtc, globalSharesBtc, AccountBalance.payoutBtc),
				StatsGlobalCoinReply(globalHashrateNmc, globalSharesNmc, AccountBalance.payoutNmc),
				StatsGlobalCoinReply(globalHashrateSlc, globalSharesSlc, AccountBalance.payoutSlc))
		}
		globalReply
	}

}

class StatComet extends CometActor {
	override def defaultPrefix = Full("stat")
	
	this ! Tick
  
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
			reply = Full(a)
			partialUpdate(
				Replace("thestats", cssSel(a).apply(defaultHtml)) &
				JsRaw("$('.last_updated').effect('highlight', 1000)").cmd
			)
			ActorPing.schedule(this, Tick, 60 seconds) 

		case a: StatsUserReply =>
			reply = Full(a)
			partialUpdate(
				Replace("thestats", cssSel(a).apply(defaultHtml)) &
				JsRaw("$('.last_updated').effect('highlight', 1000)").cmd
			)
			ActorPing.schedule(this, Tick, 30 seconds) 
	}

	private var reply: Box[StatsReply] = Empty

	def render = reply match {
		case Full(a: StatsGlobalReply) => cssSel(a)
		case Full(a: StatsUserReply) => cssSel(a)
		case _ => <span id="thestats"/>
	}

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
		".user_btc_unconfirmed *" #> "%.8f BTC".format(r.bitcoin.rewardUnconfirmed) &
		".user_btc_payout *" #> "%.8f BTC".format(r.bitcoin.totalPayout) &
		".user_nmc_hashrate *" #> "%s MH/sec".format(r.namecoin.hashrate)  &
		".user_nmc_shares_total *" #> r.namecoin.total &
		".user_nmc_shares_round *" #> r.namecoin.round &
		".user_nmc_shares_stale *" #> r.namecoin.stale &
		".user_nmc_balance *" #> "%.8f NMC".format(r.user.balance_nmc.is) &
		".user_nmc_reward *" #> "%.8f NMC".format(r.namecoin.reward) &
		".user_nmc_unconfirmed *" #> "%.8f NMC".format(r.namecoin.rewardUnconfirmed) &
		".user_nmc_payout *" #> "%.8f NMC".format(r.namecoin.totalPayout) &
		".user_slc_hashrate *" #> "%s MH/sec".format(r.solidcoin.hashrate)  &
		".user_slc_shares_total *" #> r.solidcoin.total &
		".user_slc_shares_round *" #> r.solidcoin.round &
		".user_slc_shares_stale *" #> r.solidcoin.stale &
		".user_slc_balance *" #> "%.8f SLC".format(r.user.balance_slc.is) &
		".user_slc_reward *" #> "%.8f SLC".format(r.solidcoin.reward) &
		".user_slc_unconfirmed *" #> "%.8f SLC".format(r.solidcoin.rewardUnconfirmed) &
		".user_slc_payout *" #> "%.8f SLC".format(r.solidcoin.totalPayout) &
		cssSel(r.global)

	def cssSel(r: StatsGlobalReply): CssSel =
		".last_updated *" #> <xml:group>Last updated at<br/>{DateTimeHelpers.getDate.toString("yyyy-MM-dd HH:mm:ss")}</xml:group> &
		".global_hashrate *" #> "%.1f GH/sec".format(r.hashrate / 1000.0)  &
		".global_workers *" #> r.workers &
		".global_btc_hashrate *" #> "%.3f GH/sec".format(r.bitcoin.hashrate / 1000.0)  &
		".global_btc_shares *" #> r.bitcoin.shares &
		".global_btc_payout *" #> "%.8f BTC".format(r.bitcoin.payout) &
		".global_nmc_hashrate *" #> "%.3f GH/sec".format(r.namecoin.hashrate / 1000.0)  &
		".global_nmc_shares *" #> r.namecoin.shares &
		".global_nmc_payout *" #> "%.8f NMC".format(r.namecoin.payout) &
		".global_slc_hashrate *" #> "%.3f GH/sec".format(r.solidcoin.hashrate / 1000.0)  &
		".global_slc_shares *" #> r.solidcoin.shares &
		".global_slc_payout *" #> "%.8f SLC".format(r.solidcoin.payout) &
		".blocks_row *" #> WonShare.findAll(OrderBy(WonShare.timestamp, Descending), MaxRows(50)).map(s =>
			".blocks_network *" #> s.network.is &
			".blocks_time *" #> s.timestamp.toString &
			".blocks_shares *" #> s.shares.toString &
			".blocks_confirms *" #> s.confirmations.toString &
			".blocks_id *" #> s.blockLink
		)

}


class GigahashComet extends CometActor {
	override def defaultPrefix = Full("ghstat")

	this ! Tick

	// If this actor is not used for 2 minutes, destroy it
	override def lifespan: Box[TimeSpan] = Full(2 minutes)

	override def lowPriority = {
		case Tick => StatCollector ! StatsGatherGlobal(this)

		case a: StatsGlobalReply =>
			reply = Full(a)
			partialUpdate(
				Replace("ghstats", cssSel(a).apply(defaultHtml)) &
				JsRaw("$('#ghstats').effect('highlight', 1000)").cmd
			)
			ActorPing.schedule(this, Tick, 60 seconds) 
	}
	
	private var reply: Box[StatsReply] = Empty

	def render = reply match {
		case Full(a: StatsGlobalReply) => cssSel(a)
		case _ => <span id="ghstats"/>
	}

	def cssSel(r: StatsGlobalReply): CssSel =
		"#gigahash_total *" #> "%.1f GH/sec".format(r.hashrate / 1000.0) &
		"#gigahash_btc *" #> "%.1f GH/sec".format(r.bitcoin.hashrate / 1000.0) &
		"#gigahash_nmc *" #> "%.1f GH/sec".format(r.namecoin.hashrate / 1000.0) &
		"#gigahash_slc *" #> "%.1f GH/sec".format(r.solidcoin.hashrate / 1000.0)

}

