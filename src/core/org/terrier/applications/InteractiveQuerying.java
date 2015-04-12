/*
 * Terrier - Terabyte Retriever
 * Webpage: http://terrier.org
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - School of Computing Science
 * http://www.ac.gla.uk
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is InteractiveQuerying.java.
 *
 * The Original Code is Copyright (C) 2004-2014 the University of Glasgow.
 * All Rights Reserved.
 *
 * Contributor(s):
 *   Gianni Amati <gba{a.}fub.it> (original author)
 *   Vassilis Plachouras <vassilis{a.}dcs.gla.ac.uk>
 *   Ben He <ben{a.}dcs.gla.ac.uk>
 *   Craig Macdonald <craigm{a.}dcs.gla.ac.uk>
 *
 * 	 Luca Soldaini <luca{a.}ir.cs.georgetown.edu (branch)
 */
package org.terrier.applications;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import org.terrier.matching.ResultSet;
import org.terrier.querying.Manager;
import org.terrier.querying.SearchRequest;
import org.terrier.structures.Index;
import org.terrier.structures.MetaIndex;
import org.terrier.utility.ApplicationSetup;
/**
 * This class performs interactive querying at the command line. It asks
 * for a query on Standard Input, and then displays the document IDs that
 * match the given query.
 * <p><b>Properties:</b>
 * <ul><li><tt>interactive.model</tt> - which weighting model to use, defaults to PL2</li>
 * <li><tt>interactive.matching</tt> - which Matching class to use, defaults to Matching</li>
 * <li><tt>interactive.manager</tt> - which Manager class to use, defaults to Matching</li>
 * </ul>
 * @author Gianni Amati, Vassilis Plachouras, Ben He, Craig Macdonald
 */
public class InteractiveQuerying {
	/** The logger used */
	protected static final Logger logger = Logger.getLogger(InteractiveQuerying.class);

	/** Change to lowercase? */
	protected final static boolean lowercase = Boolean.parseBoolean(ApplicationSetup.getProperty("lowercase", "true"));
	/** display user prompts */
	protected boolean verbose = true;
	/** the number of processed queries. */
	protected int matchingCount = 0;
	/** The file to store the output to.*/
	protected PrintWriter resultFile = new PrintWriter(System.out);
	/** The name of the manager object that handles the queries. Set by property <tt>trec.manager</tt>, defaults to Manager. */
	protected String managerName = ApplicationSetup.getProperty("interactive.manager", "Manager");
	/** The query manager.*/
	protected Manager queryingManager;
	/** The weighting model used. */
	protected String wModel = ApplicationSetup.getProperty("interactive.model", "PL2");
	/** The matching model used.*/
	protected String mModel = ApplicationSetup.getProperty("interactive.matching", "Matching");
	/** The data structures used.*/
	protected Index index;

	/** Set true to perform retrieving */
	protected static boolean retrieving = false;

	/** Set true to perform counting */
	protected static boolean counting = false;

	protected String[] metaKeys = ApplicationSetup.getProperty("interactive.output.meta.keys", "docno").split("\\s*,\\s*");

	/** A default constructor initialises the index, and the Manager. */
	public InteractiveQuerying() {
		loadIndex();
		createManager();
	}

	/**
	* Create a querying manager. This method should be overriden if
	* another matching model is required.
	*/
	protected void createManager(){
		try{
		if (managerName.indexOf('.') == -1)
			managerName = "org.terrier.querying."+managerName;
		else if (managerName.startsWith("uk.ac.gla.terrier"))
			managerName = managerName.replaceAll("uk.ac.gla.terrier", "org.terrier");
		queryingManager = (Manager) (Class.forName(managerName)
			.getConstructor(new Class[]{Index.class})
			.newInstance(new Object[]{index}));
		} catch (Exception e) {
			logger.error("Problem loading Manager ("+managerName+"): ",e);
		}
	}

	/**
	* Loads index(s) from disk.
	*
	*/
	protected void loadIndex(){
		long startLoading = System.currentTimeMillis();
		index = Index.createIndex();
		if(index == null)
		{
			logger.fatal("Failed to load index. Perhaps index files are missing");
		}
		long endLoading = System.currentTimeMillis();
		if (logger.isInfoEnabled())
			logger.info("time to intialise index : " + ((endLoading-startLoading)/1000.0D));
	}
	/**
	 * Closes the used structures.
	 */
	public void close() {
		try{
			index.close();
		} catch (IOException ioe) {
			logger.warn("Problem closing index", ioe);
		}

	}
	/**
	 * According to the given parameters, it sets up the correct matching class.
	 * @param queryId String the query identifier to use.
	 * @param query String the query to process.
	 * @param cParameter double the value of the parameter to use.
	 */
	public void processQuery(String queryId, String query, double cParameter) {
		SearchRequest srq = queryingManager.newSearchRequest(queryId, query);
		srq.setControl("c", Double.toString(cParameter));
		srq.addMatchingModel(mModel, wModel);
		matchingCount++;

		queryingManager.runPreProcessing(srq);
		queryingManager.runMatching(srq);
		queryingManager.runPostProcessing(srq);
		queryingManager.runPostFilters(srq);
		try{
			printResults(resultFile, srq);
		} catch (IOException ioe) {
			logger.error("Problem displaying results", ioe);
		}

	}

	public void countQuery(String queryId, String query, double cParameter) {
		SearchRequest srq = queryingManager.newSearchRequest(queryId, query);
		Index.setIndexLoadingProfileAsRetrieval(false);
		Index i = Index.createIndex();
		String numdoc = String.valueOf(i.getCollectionStatistics().getNumberOfDocuments());
		ApplicationSetup.setProperty("matching.retrieved_set_size", numdoc);
		Index.setIndexLoadingProfileAsRetrieval(true);

		int resultSetSize = 0;

		srq.setControl("c", Double.toString(cParameter));
		srq.addMatchingModel(mModel, wModel);
		matchingCount++;

		boolean noPostingList = false;
		try {
			queryingManager.runPreProcessing(srq);
			queryingManager.runMatching(srq);
			queryingManager.runPostProcessing(srq);
			queryingManager.runPostFilters(srq);
		} catch (NullPointerException ne){
			noPostingList = true;
			resultSetSize = 0;
		}
		if (! noPostingList) {
			try{
				resultSetSize = srq.getResultSet().getExactResultSize();
			} catch (NullPointerException ne) {
				resultSetSize = 0;
			}
		}

		System.out.println("\nOUTPUT - " + resultSetSize + " matching documents\n");
	}
	/**
	 * Performs the matching using the specified weighting model
	 * from the setup and possibly a combination of evidence mechanism.
	 * It parses the file with the queries (the name of the file is defined
	 * in the address_query file), creates the file of results, and for each
	 * query, gets the relevant documents, scores them, and outputs the results
	 * to the result file.
	 * @param cParameter the value of c
	 */
	public void processQueries(double cParameter) {
		try {
			//prepare console input
			InputStreamReader consoleReader = new InputStreamReader(System.in);
			BufferedReader consoleInput = new BufferedReader(consoleReader);
			String query; int qid=1;
			if (verbose)
				System.out.print("Please enter your query: ");
			while ((query = consoleInput.readLine()) != null) {
				if (query.length() == 0 ||
					query.toLowerCase().equals("quit") ||
					query.toLowerCase().equals("exit")
				)
				{
					return;
				}
				processQuery(""+(qid++), lowercase ? query.toLowerCase() : query, cParameter);
				if (verbose)
					System.out.print("Please enter your query: ");
			}
		} catch(IOException ioe) {
			logger.error("Input/Output exception while performing the matching. Stack trace follows.",ioe);
		}
	}
	/**
	 * Prints the results
	 * @param pw PrintWriter the file to write the results to.
	 * @param q SearchRequest the search request to get results from.
	 */
	public void printResults(PrintWriter pw, SearchRequest q) throws IOException {
		ResultSet set = q.getResultSet();
		int[] docids = set.getDocids();
		double[] scores = set.getScores();
		int minimum = Integer.parseInt(ApplicationSetup.getProperty("interactive.output.format.length", "1000"));
		//if the minimum number of documents is more than the
		//number of documents in the results, aw.length, then
		//set minimum = aw.length
		if (minimum > set.getResultSize()){
			minimum = set.getResultSize();
		}

		if(set.getResultSize() > 0){
			pw.write("\nOUTPUT - Displaying 1-" + set.getResultSize() + " results\n");
		}
		else{
			pw.write("\nOUTPUT - No results\n");
		}

		int metaKeyId = 0; final int metaKeyCount = metaKeys.length;
		String[][] docNames = new String[metaKeyCount][];
		for(String metaIndexDocumentKey : metaKeys)
		{
			if (set.hasMetaItems(metaIndexDocumentKey))
			{
				docNames[metaKeyId] = set.getMetaItems(metaIndexDocumentKey);
			}
			else
			{
				final MetaIndex metaIndex = index.getMetaIndex();
				docNames[metaKeyId] = metaIndex.getItems(metaIndexDocumentKey, docids);
			}
			metaKeyId++;
		}


		StringBuilder sbuffer = new StringBuilder();
		//the results are ordered in asceding order
		//with respect to the score. For example, the
		//document with the highest score has score
		//score[scores.length-1] and its docid is
		//docid[docids.length-1].
		int start = 0;
		int end = minimum;
		for (int i = start; i < end; i++) {
			if (scores[i] <= 0d)
				continue;
			sbuffer.append(i);
			sbuffer.append(" ");
			for(metaKeyId = 0; metaKeyId < metaKeyCount; metaKeyId++)
			{
				sbuffer.append(docNames[metaKeyId][i]);
				sbuffer.append(" ");
			}
			sbuffer.append(docids[i]);
			sbuffer.append(" ");
			sbuffer.append(scores[i]);
			sbuffer.append('\n');
		}
		pw.write(sbuffer.toString());
		pw.write('\n');
		pw.flush();
	}


	protected static class Counter{
	 	private int count;

		public Counter(int baseCount) {
			this.count = baseCount;
		}

		public Counter() {
			this.count = 0;
		}

		public int getCount() {
			return this.count;
		}

		public void incrementCount(){
			this.count++;
		}
	}


	protected static String parseString(String[] args, Counter pos){
		StringBuilder s = new StringBuilder();

		while ((pos.getCount() < args.length) && !(args[pos.getCount()].startsWith("-"))){

			s.append(args[pos.getCount()]);
			s.append(" ");
			pos.incrementCount();
		}
		return s.toString();
	}

	/**
	 * Starts the interactive query application.
	 * @param args the command line arguments.
	 */
	public static void main(String[] args) {

		InteractiveQuerying iq = new InteractiveQuerying();

		if (args.length == 0) {
			iq.processQueries(1.0);
		} else if (args.length == 1 && args[0].equals("--noverbose")) {
			iq.verbose = false;
			iq.processQueries(1.0);
		} else {
			Counter pos = new Counter(0);
			boolean applicationSetupUpdated = false;
			String raw_query = "";
			Double c = 1.0;

			while (pos.getCount() < args.length) {
				if (args[pos.getCount()].startsWith("-D")){
					String[] propertyKV = args[pos.getCount()].replaceFirst("^-D", "").split("=");
					if (propertyKV.length == 1) {
						propertyKV = new String[]{propertyKV[0], ""};
					}
					ApplicationSetup.setProperty(propertyKV[0], propertyKV[1]);
					applicationSetupUpdated = true;
				} else if (args[pos.getCount()].equals("-r") || args[pos.getCount()].equals("--retrieve")){

					pos.incrementCount();
					raw_query = parseString(args, pos);

					if (raw_query == "") {
						logger.error("No query after -r");
					}

					retrieving = true;
				} else if (args[pos.getCount()].equals("-C") || args[pos.getCount()].equals("--count")){

					pos.incrementCount();
					raw_query = parseString(args, pos);

					if (raw_query == "") {
						logger.error("No query after -c");
					}

					counting = true;
				} else if (args[pos.getCount()].equals("--nonverbose")){
					iq.verbose = false;
					logger.setLevel(Level.ERROR);
				} else if (args[pos.getCount()].startsWith("-c")) {
				if (args[pos.getCount()].length()==2) { //the next argument is the value
					if ((pos.getCount() + 1) < args.length) { //there is another argument
						pos.incrementCount();
						c = Double.parseDouble(args[pos.getCount()]);
					} else
						logger.error("c value can't be parsed");
				} else { //the value is in the same argument
					c = Double.parseDouble(args[pos.getCount()].substring(2));
				}
			}

			pos.incrementCount();
			}


			ArrayList <String> queries = new ArrayList <String>();
		 	Matcher m = Pattern.compile("[^,]+").matcher(raw_query);
		 	while (m.find()) {
				queries.add(m.group().trim());
		 	}

			for (String query : queries){
				logger.info("query : " + query);
				if (retrieving){
					iq.processQuery("CMDLINE", query, c);
				} else if(counting){
					iq.countQuery("CMDLINE", query, c);
				}
			}

		}
	}
}
