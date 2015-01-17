/*
 * Terrier - Terabyte Retriever 
 * Webpage: http://terrier.org 
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - School of Computing Science
 * http://www.gla.ac.uk/
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
 * The Original Code is CollectionEnrichment.java.
 *
 * The Original Code is Copyright (C) 2004-2014 the University of Glasgow.
 * All Rights Reserved.
 *
 * Contributor(s):
 *   Richard McCreadie <richard.mccreadie@glasgow.ac.uk>
 *   
 */

package org.terrier.applications.secondary;


import org.terrier.querying.Manager;
import org.terrier.querying.SearchRequest;
import org.terrier.structures.Index;
import org.terrier.utility.ApplicationSetup;

/**
 * Provides a simple method to get a set of expansion terms and weights for a given query
 * and index.
 * 
 * @author Richard McCreadie
 * @since 4.0
 */
public class CollectionEnrichment {

	
	public static String[] getExpansionTerms(String query, Index index, String rankingModel, String termWeightingModel, int numExpansionDocs, int numExpansionTerms) {
		
		StringBuffer sb = new StringBuffer();
		
		sb.append(normaliseString(query));
		
		ApplicationSetup.EXPANSION_TERMS = numExpansionTerms;
		ApplicationSetup.EXPANSION_DOCUMENTS = numExpansionDocs;
		
		Manager queryingManager = new Manager(index);

		SearchRequest srq = queryingManager.newSearchRequest("ce", sb.toString());
		srq.addMatchingModel("Matching",rankingModel);
		srq.setOriginalQuery(sb.toString());
		srq.setControl("qe", "on");
		srq.setControl("qemodel", termWeightingModel);
		queryingManager.runPreProcessing(srq);
		queryingManager.runMatching(srq);
		queryingManager.runPostProcessing(srq);
		queryingManager.runPostFilters(srq);
		
		
		String newQuery = srq.getControl("QE.ExpandedQuery");
		return newQuery.split(" ");
	}
	
	
	public static String normaliseString(String text) {
		//System.err.println(normaliseString("IN: "+text));
		String normaltext = text.toLowerCase();
		normaltext = normaltext.replace("(", " ");
		normaltext = normaltext.replace(")", " ");
		normaltext = normaltext.replace("\"", " ");
		normaltext = normaltext.replace("'", " ");
		normaltext = normaltext.replace("?"," ");
		normaltext = normaltext.replace(","," ");
		normaltext = normaltext.replace(" and"," ");
		normaltext = normaltext.replace(" or"," ");
		normaltext = normaltext.replace(".", " ");
		normaltext = normaltext.replace("  ", " ");
		normaltext = normaltext.replace("+", "");
		//normaltext = normaltext.replace("-", " ");
		normaltext = normaltext.replace("[", "");
		normaltext = normaltext.replace("]", "");
		normaltext = normaltext.trim();
		String[] parts = normaltext.split(" ");
		StringBuffer newQuery = new StringBuffer();
		for (String term : parts) {
			if (term.contains(":")) {
				if (term.endsWith(":")) term = term.replace(":", "");
				else term = term.split(":")[1];
			}
			newQuery.append(" "+term);
		}
		//System.err.println(normaliseString("OUT: "+newQuery.toString().trim()));
		return newQuery.toString().trim();
	}
}
