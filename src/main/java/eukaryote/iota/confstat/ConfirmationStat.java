package eukaryote.iota.confstat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.common.util.concurrent.RateLimiter;

import jota.IotaAPI;
import jota.dto.response.FindTransactionResponse;
import jota.dto.response.GetBalancesResponse;
import jota.dto.response.GetBundleResponse;
import jota.dto.response.GetInclusionStateResponse;
import jota.error.ArgumentException;
import jota.error.InvalidBundleException;
import jota.error.InvalidSignatureException;
import jota.error.NoNodeInfoException;
import jota.model.Transaction;

public class ConfirmationStat {
	IotaAPI api;
	final RateLimiter rateLimiter = RateLimiter.create(2);
	
	// empty transaction array
	private static final Transaction[] txnarr = {};

	public ConfirmationStat(IotaAPI api) {
		this.api = api;
	}

	public Status statusOf(String txnhash) throws NoNodeInfoException {
		return statusOf(api.getTransactionsObjects(new String[] { txnhash }).get(0));
	}

	public Status statusOf(Transaction txn) throws NoNodeInfoException {
		// check inclusion on latest milestone
		
		rateLimiter.acquire();
		
		if (txn.getPersistence() != null && txn.getPersistence())
			return Status.CONFIRMED;
		
		GetInclusionStateResponse incl = api.getLatestInclusion(new String[] { txn.getHash() });
		
		if (incl.getStates()[0])
			return Status.CONFIRMED;

		FindTransactionResponse bundle = api.findTransactionsByBundles(txn.getBundle());
		
		List<Transaction> txs = api.getTransactionsObjects(bundle.getHashes());

		List<String> expectedaddrs = new LinkedList<>();
		long expectedtotal = 0;
		
		for (Transaction t : txs) {
			if (t.getValue() >= 0)
				continue;
			
			expectedaddrs.add(t.getAddress());
			expectedtotal += -t.getValue();
		}
		
		GetBalancesResponse balances = api.getBalances(1, expectedaddrs);
		for (String bal : balances.getBalances()) {
			long val = Long.parseLong(bal);
			expectedtotal -= val;
		}
		
		if (expectedtotal > 0)
			return Status.DOUBLESPENT;
		
		return Status.PENDING;	
	}
}
