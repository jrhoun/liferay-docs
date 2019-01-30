package com.liferay.documentation.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.htmlparser.Parser;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;

import com.liferay.portal.kernel.util.Validator;

public class CheckLinks {

	public static void main(String[] args) throws Exception {

		String legacyLinks = args[0];
		checkLegacyLinks = Boolean.parseBoolean(legacyLinks);

		String apiLinks = args[1];
		checkApiLinks = Boolean.parseBoolean(apiLinks);

		String docDir = args[2];
		platformToken = args[3];
		appToken = args[4];
		platformReferenceSite = args[5];
		appReferenceSite = args[6];

		String dxpLinks = args[7];
		checkDxpLinks = Boolean.parseBoolean(dxpLinks);

		// e.g., docDir = tutorials
		File currentArticleDir = new File("../" + docDir + "/articles");
		List<File> currentArticles = findCurrentDirArticles(currentArticleDir);

		if (checkDxpLinks) {
			currentArticles = addDxpOnlyArticles(currentArticles, docDir, true);
		}

		assignReferencedDirArticles(articleDirs);

		userGuideHeaders = findHeaders(userGuideArticles);
		deploymentGuideHeaders = findHeaders(deploymentGuideArticles);
		distributeGuideHeaders = findHeaders(distributeGuideArticles);
		analyticsCloudHeaders = findHeaders(analyticsCloudArticles);
		commerceHeaders = findHeaders(commerceArticles);
		userGuideReferenceHeaders = findHeaders(userGuideReferenceArticles);
		appDevHeaders = findHeaders(appDevArticles);
		frameworksDevHeaders = findHeaders(frameworksDevArticles);
		customizationDevHeaders = findHeaders(customizationDevArticles);
		tutorialsHeaders = findHeaders(tutorialsArticles);
		referenceDevHeaders = findHeaders(referenceDevArticles);
		commerceDevHeaders = findHeaders(commerceDevArticles);

		for (File article : currentArticles) {

			LineNumberReader in = new LineNumberReader(new FileReader(article));
			String line = null;

			while ((line = in.readLine()) != null) {

				if (line.contains("](/deployment/") || line.contains("](/developer/") ||
						line.contains("](/discover/") || line.contains("](/distribute/") ||
						line.contains("](/web/commerce/") || line.contains("](/user/")) {

					String findStr = "/-/knowledge_base/";
					int urlsInLine = countStrings(line, findStr);

					if (urlsInLine < 2) {

						String header = extractHeader(line, article, in, findStr);

						String primaryHeader = null;
						validUrl = true;

						if (header.contains("#")) {
							String[] splitHeaders = header.split("#");

							primaryHeader = splitHeaders[0];
							String secondaryHeader = splitHeaders[1];

							validUrl = isUrlValid(line, article, in, primaryHeader, secondaryHeader, 0, false);
						}
						else if (header.equals("")) {
							continue;
						}
						else {

							primaryHeader = header;
							validUrl = isUrlValid(line, article, in, primaryHeader, null, 0, false);
						}

						if (!validUrl) {
							logInvalidUrl(article, in.getLineNumber(), line, false);
						}
					}

					else {
						checkMultiLinks(article, line, in, findStr);
					}
				}
				if (line.contains("](#")) {

					String findStr = "](#";
					int subHeadersInLine = countStrings(line, findStr);

					if (subHeadersInLine < 2) {
						String secondaryHeader = extractSubHeader(line, article, in);

						validUrl = isSubUrlValid(article, secondaryHeader);

						if (!validUrl) {
							logInvalidUrl(article, in.getLineNumber(), line, false);
						}
					}
					else {
						checkMultiSubLinks(article, line, in, findStr);
					}

				}
				if (checkApiLinks && line.contains("/javadocs/")
						&& line.contains("/com/liferay/")) {

					validUrl = isApiUrlValid(article, in, line);

					if (!validUrl) {
						logInvalidUrl(article, in.getLineNumber(), line, false);
					}
				}
			}

			in.close();
		}
		if (resultsNumber > 0) {
			throw new Exception("\n\n**Total Broken Links: " + resultsNumber + "**\n");
		}
		else {
			System.out.println("\nNo Broken Links!");
		}
	}

	/**
	 * Adds new DXP articles and applies DXP overrides to the article list.
	 *
	 * @param  articles the CE articles
	 * @param  path the partial folder path with the DXP articles to acquire
	 * @param  currentDir whether the DXP articles are being returned for the
	 *         folder the command was executed from
	 * @return the new list of articles containing the new DXP articles and DXP
	 *         overrides
	 */
	private static List<File> addDxpOnlyArticles(List<File> articles, String path, boolean currentDir) {

		List<File> dxpArticles = getDxpArticles(path, currentDir);

		articles = includeDxpOverrides(articles, dxpArticles);

		List<File> dxpArticlesToDelete = new ArrayList<File>();

		// DXP articles that override existing CE articles are removed from DXP list so
		// they're not added as new articles
		for (File dxpArticle : dxpArticles) {
			if (fileOverrides.contains(dxpArticle)) {
				dxpArticlesToDelete.add(dxpArticle);
			}
		}

		for (File dxpArticleToDelete : dxpArticlesToDelete) {
			dxpArticles.remove(dxpArticleToDelete);
		}

		for (File dxpArticle : dxpArticles) {
			articles.add(dxpArticle);
		}

		return articles;
	}

	/**
	 * Returns all the possible referenced headers. This method scans the
	 * current line and assigns it to the appropriate header list.
	 *
	 * @param  line the line containing a relative URL
	 * @param  lineIndex the header's index on the line. This is useful when
	 *         there are multiple relative links on one line.
	 * @return the referenced headers
	 */
	private static ArrayList<List<String>> assignDirHeaders(String line, int lineIndex) {

		ArrayList<List<String>> headers = new ArrayList<List<String>>();

		String lineSubstring = line.substring(lineIndex, line.length());		

		// The first link in the substring will have its headers applied.
		if (lineSubstring.contains(userGuideDir)) {
			headers = userGuideHeaders;
		}

		else if (lineSubstring.contains(deploymentGuideDir)) {
			headers = deploymentGuideHeaders;
		}

		else if (lineSubstring.contains(commerceDir)) {
			headers = commerceHeaders;
		}

		else if (lineSubstring.contains(analyticsCloudDir)) {
			headers = analyticsCloudHeaders;
		}

		else if (lineSubstring.contains(userGuideReferenceDir)) {
			headers = userGuideReferenceHeaders;
		}

		else if (lineSubstring.contains(distributeGuideDir)) {
			headers = distributeGuideHeaders;
		}

		else if (lineSubstring.contains(appDevDir)) {
			headers = appDevHeaders;
		}

		else if (lineSubstring.contains(customizationDevDir)) {
			headers = customizationDevHeaders;
		}

		else if (lineSubstring.contains(frameworksDevDir)) {
			headers = frameworksDevHeaders;
		}

		else if (lineSubstring.contains(tutorialsDir)) {
			headers = tutorialsHeaders;
		}

		else if (lineSubstring.contains(referenceDevDir)) {
			headers = referenceDevHeaders;
		}

		else if (lineSubstring.contains(commerceDevDir)) {
			headers = commerceDevHeaders;
		}

		return headers;
	}

	/**
	 * Finds the articles from other possible referenced directories and sets
	 * them to the appropriate local variables.
	 * 
	 * @param dirs the popular directories residing in the
	 *        <code>liferay-docs</code> repository
	 */
	private static void assignReferencedDirArticles(String[] dirs) {

		for (String articleDir : articleDirs) {

			if (articleDir.equals(userGuideDir)) {
				userGuideArticles = findArticles(userGuideDir);
			}

			if (articleDir.equals(deploymentGuideDir)) {
				deploymentGuideArticles = findArticles(deploymentGuideDir);
			}

			if (articleDir.equals(distributeGuideDir)) {
				distributeGuideArticles = findArticles(distributeGuideDir);
			}

			if (articleDir.equals(commerceDir)) {
				commerceArticles = findArticles(commerceGithubDir);
			}

			if (articleDir.equals(analyticsCloudDir)) {
				analyticsCloudArticles = findArticles(analyticsCloudDir);
			}

			if (articleDir.equals(userGuideReferenceDir)) {
				userGuideReferenceArticles = findArticles(userGuideReferenceDir);
			}

			if (articleDir.equals(appDevDir)) {
				appDevArticles = findArticles(appDevDir);
			}

			if (articleDir.equals(customizationDevDir)) {
				customizationDevArticles = findArticles(customizationDevDir);
			}

			if (articleDir.equals(frameworksDevDir)) {
				frameworksDevArticles = findArticles(frameworksDevDir);
			}

			if (articleDir.equals(tutorialsDir)) {
				tutorialsArticles = findArticles(tutorialsDir);
			}

			if (articleDir.equals(referenceDevDir)) {
				referenceDevArticles = findArticles(referenceDevDir);
			}

			if (articleDir.equals(commerceDevDir)) {
				commerceDevArticles = findArticles(commerceGithubDevDir);
			}
		}
	}

	/**
	 * Checks the line that contains multiple relative links.
	 *
	 * @param  article the article containing the line
	 * @param  line the line containing multiple relative links
	 * @param  in the line number reader
	 * @param  findStr the string used for indexing the line's links
	 * @throws IOException if an IO exception occurred
	 */
	private static void checkMultiLinks(File article, String line, LineNumberReader in, String findStr)
			throws IOException {

		// Extract headers into map with <header, index> pairs
		LinkedHashMap<String, Integer> headerMaps = extractMultiStrings(line, article, in, findStr, 4);

		Iterator<?> it = headerMaps.entrySet().iterator();

		// Iterating through header maps, which contain header and index information
		// used for validating lines with multiple links
	    while (it.hasNext()) {

	    	@SuppressWarnings("rawtypes")
			Map.Entry pair = (Map.Entry)it.next();
	        it.remove();

			String header = pair.getKey().toString();
			String headerValue = pair.getValue().toString();
			int headerIndex = Integer.parseInt(headerValue);

			// Find version for each header so we can accurately check them
			String substringLineStart = line.substring(headerIndex);
			int headerStart = substringLineStart.indexOf(findStr) + findStr.length();
			String version = substringLineStart.substring(headerStart, headerStart + 3);
			boolean differingDefaultVersion = false;

			if (!version.equals(PORTAL_VERSION)) {
				differingDefaultVersion = true;
			}

			// end of >1 logic

			String primaryHeader = null;
			validUrl = true;

			if (header.contains("#")) {
				String[] splitHeaders = header.split("#");

				primaryHeader = splitHeaders[0];
				String secondaryHeader = splitHeaders[1];

				validUrl = isUrlValid(line, article, in, primaryHeader, secondaryHeader, headerIndex, differingDefaultVersion);
			}
			else if (header.equals("")) {
				continue;
			}
			else {

				primaryHeader = header;
				validUrl = isUrlValid(line, article, in, primaryHeader, null, headerIndex, differingDefaultVersion);
			}

			if (!validUrl) {
				logInvalidUrl(article, in.getLineNumber(), line, false);
				System.out.println("Invalid Header: " + header + "\n");
			}
		}

	}

	/**
	 * Checks the line that contains multiple subheader relative links.
	 *
	 * @param  article the article containing the line
	 * @param  line the line containing multiple subheader relative links
	 * @param  in the line number reader
	 * @param  findStr the string used for indexing the line's links
	 * @throws IOException if an IO exception occurred
	 */
	private static void checkMultiSubLinks(File article, String line, LineNumberReader in,
			String findStr) throws IOException {

		LinkedHashMap<String, Integer> headerMaps = extractMultiStrings(line, article, in, findStr, 0);

		Iterator<?> it = headerMaps.entrySet().iterator();

		// Iterating through header maps, which contain header and index information
		// used for validating lines with multiple links
	    while (it.hasNext()) {

	    	@SuppressWarnings("rawtypes")
			Map.Entry pair = (Map.Entry)it.next();
	        it.remove();

			String secondaryHeader = pair.getKey().toString();

			validUrl = isSubUrlValid(article, secondaryHeader);

			if (!validUrl) {
				logInvalidUrl(article, in.getLineNumber(), line, false);
				System.out.println("Invalid Subheader: #" + secondaryHeader + "\n");
			}
	    }
	}

	/**
	 * Returns the number of specific strings in the line.
	 *
	 * @param  line the line to count the number of specific strings
	 * @param  findStr the specific string to search for
	 * @return the number of specific strings in the line
	 */
	private static int countStrings(String line, String findStr) {

		int lastIndex = 0;
		int count = 0;

		while(lastIndex != -1){

		    lastIndex = line.indexOf(findStr,lastIndex);

		    if(lastIndex != -1){
		        count ++;
		        lastIndex += findStr.length();
		    }
		}
		return count;
	}

	/**
	 * Returns the header ID contained in the given line. For example, the
	 * following line:
	 * 
	 * <p>
	 * <pre>
	 * <code>
	 * [here](/discover/deployment/-/knowledge_base/7-1/installing-liferay-portal#liferay-home)
	 * </code>
	 * </pre>
	 * </p>
	 *
	 * returns
	 *
	 * <p>
	 * <pre>
	 * <code>
	 * installing-liferay-portal#liferay-home
	 * </code>
	 * </pre>
	 * </p>
	 *
	 * @param  line the line from which to extract the header
	 * @param  article the article containing the line
	 * @param  in the line number reader
	 * @param  findStr the string to search for. This is helpful to prevent
	 *         false positives when searching for a header.
	 * @return the header ID
	 * @throws IOException if an IO exception occurred
	 */
	private static String extractHeader(String line, File article, LineNumberReader in, String findStr)
			throws IOException {

		int strIndex = line.indexOf(findStr);
		int begIndex = strIndex + findStr.length() + 4;
		int endIndex = line.indexOf(")", begIndex);

		String header = "";

		try {
			header = line.substring(begIndex, endIndex);
		} catch(Exception e) {
			logInvalidUrl(article, in.getLineNumber(), line, true);
		}

		return header;
	}

	/**
	 * Returns a map of headers paired with their line indexes. This method is
	 * used to extract multiple headers (from relative links) that are contained
	 * on one line.
	 *
	 * @param  line the line from which to extract the header
	 * @param  article the article containing the line
	 * @param  in the line number reader
	 * @param  findStr the string to search for. This is helpful to prevent
	 *         false positives when searching for a header.
	 * @param  indexCorrection the number used to modify the index used when
	 *         searching for the next specific string on the line
	 * @return the multiple headers contained on the line paired with their indexes
	 * @throws IOException if an IO exception occurred
	 */
	private static LinkedHashMap<String, Integer> extractMultiStrings(String line, File article, LineNumberReader in,
			String findStr, int indexCorrection) throws IOException {

		// Find all relevant headers
		LinkedHashMap<String, Integer> headerMap = new LinkedHashMap<String, Integer>();
		String originalLine = line;

		while(line.contains(findStr)){

			int strIndex = line.indexOf(findStr);
			int begIndex = strIndex + findStr.length() + indexCorrection;
			int endIndex = line.indexOf(")", begIndex);
			int headerIndex = originalLine.length() - line.length();

			String header = "";

			try {
				header = line.substring(begIndex, endIndex);
			} catch(Exception e) {
				logInvalidUrl(article, in.getLineNumber(), line, true);
			}

			line = line.substring(endIndex, line.length());

			headerMap.put(header, headerIndex);
			//headerList.add(map);

		}

		return headerMap;
	}

	/**
	 * Returns the fully qualified LDN URL from the given line.
	 *
	 * @param  line the line from which to extract the URL
	 * @param  lineNumber the line number
	 * @param  fileName the article's name
	 * @return the LDN URL
	 * @throws IOException if an IO exception occurred
	 */
	private static String extractLdnUrl(String line, int lineNumber, File article)
			throws IOException {

		int begIndex = line.indexOf("](/") + 2;
		int endIndex = line.indexOf(")", begIndex);
		String endLdnUrl = null;

		try{
			endLdnUrl = line.substring(begIndex, endIndex);
		} catch (StringIndexOutOfBoundsException e) {
			endLdnUrl = line.substring(begIndex, line.length());
			logInvalidUrl(article, lineNumber, line, true);
		}

		ldnArticle = endLdnUrl;

		String begLdnUrl = "https://dev.liferay.com";

		String ldnUrl = begLdnUrl.concat(endLdnUrl);

		return ldnUrl;
	}

	/**
	 * Returns the sub-header ID contained in the given line. A sub-header is a
	 * header characterizing a subsection in the article.
	 *
	 * @param  line the line from which to extract the URL
	 * @param  article the article containing the line
	 * @param  in the line number reader
	 * @return the sub-header ID
	 * @throws IOException if an IO exception occurred
	 */
	private static String extractSubHeader(String line, File article, LineNumberReader in)
			throws IOException {

		int begIndex = line.indexOf("](#") + 3;
		int endIndex = line.indexOf(")", begIndex);

		String header = "";

		try {
			header = line.substring(begIndex, endIndex);
		} catch(Exception e) {
			logInvalidUrl(article, in.getLineNumber(), line, true);
		}

		return header;
	}

	/**
	 * Returns the Markdown articles contained in the given path.
	 *
	 * @param  path the partial path for the articles (e.g.,
	 *         <code>develop/tutorials</code>
	 * @return the Markdown articles
	 */
	private static List<File> findArticles(String path) {

		File dir = new File("../" + path + "/articles");

		// Some 'articles' folders are nested in one folder while others are
		// nested two folders deep. This if statement assigns those articles
		// that are nested two folders deep.
		if(!dir.exists()) {
			dir = new File("../../" + path + "/articles");
		}

		File[] dirFiles = dir.listFiles();
		List<File> articles = new ArrayList<File>();

		Queue<File> q = new LinkedList<File>();
		for (File f : dirFiles) {
			q.add(f);
		}

		while (!q.isEmpty()) {
			File f = q.remove(); 

			if (f.isDirectory()) {
				File[] files = f.listFiles();

				for (File file : files) {
					q.add(file);
				}
			}
			else {
				if (f.getName().endsWith(".markdown")) {
					articles.add(f);
				}
			}
		}

		if (checkDxpLinks) {
			articles = addDxpOnlyArticles(articles, path, false);
		}

		return articles;
	}

	/**
	 * Returns the Markdown articles located in the current directory.
	 *
	 * @param  articleDir the current directory from which the Ant task was
	 *         executed
	 * @return the current directory's Markdown articles
	 */
	private static List<File> findCurrentDirArticles(File articleDir) {

		File[] articleDirFiles = articleDir.listFiles();
		List<File> articles = new ArrayList<File>();

		Queue<File> q = new LinkedList<File>();
		for (File f : articleDirFiles) {
			q.add(f);
		}

		while (!q.isEmpty()) {
			File f = q.remove(); 

			if (f.isDirectory()) {
				File[] files = f.listFiles();

				for (File file : files) {
					q.add(file);
				}
			}
			else {
				if (f.getName().endsWith(".markdown")) {
					articles.add(f);
				}
			}
		}

		return articles;
	}

	/**
	 * Returns the headers (primary and secondary) of the given articles. The
	 * headers are returned as an array list: primary headers contained in index
	 * <code>0</code> and secondary headers contained in index <code>1</code>.
	 *
	 * @param  articles the articles for which to find headers
	 * @return the primary and secondary headers
	 * @throws IOException if an IO exception occurred
	 */
	private static ArrayList<List<String>> findHeaders(List<File> articles) throws IOException {

		List<String> primaryHeaders = new ArrayList<String>();
		List<String> secondaryHeaders = new ArrayList<String>();

		for (File article : articles) {

			LineNumberReader in = new LineNumberReader(new FileReader(article));
			String line = null;

			while ((line = in.readLine()) != null) {

				if (line.contains("#") && line.contains("[](id=")) {

					int begIndex = line.indexOf("[](id=") + 6;
					int endIndex = line.lastIndexOf(")");
					String header = line.substring(begIndex, endIndex);

					if (line.contains("##")) {
						secondaryHeaders.add(header);
					}
					else {
						primaryHeaders.add(header);
					}
				}

			}
			in.close();
		}

		ArrayList<List<String>> headers = new ArrayList<List<String>>();
		headers.add(primaryHeaders);
		headers.add(secondaryHeaders);

		return headers;
	}

	/**
	 * Returns the DXP articles contained in the folder.
	 *
	 * @param  path the partial folder path with the DXP articles to acquire
	 * @param  currentDir whether the DXP articles are being returned for the
	 *         folder the command was executed from
	 * @return the DXP articles contained in the folder
	 */
	private static List<File> getDxpArticles(String path, boolean currentDir) {

		List<File> dxpArticles = new ArrayList<File>();
		File dxpArticleDir = new File("");

		// Ensure "articles-dxp" folder exists
		try {
			if (currentDir) {
				dxpArticleDir = new File("../" + path + "/articles-dxp");
			}
			else {
				dxpArticleDir = new File("../../" + path + "/articles-dxp");
			}

			dxpArticles = findCurrentDirArticles(dxpArticleDir);
		} catch(NullPointerException e) {
			if (currentDir) {
				System.out.println("No DXP articles in " + dxpArticleDir.getParent());
			}
		}

		return dxpArticles;
	}

	/**
	 * Overrides the CE articles with their DXP article counterparts.
	 *
	 * @param  articles the CE articles
	 * @param  dxpArticles the DXP articles
	 * @return the new list of articles containing the DXP article overrides
	 */
	private static List<File> includeDxpOverrides(List<File> articles, List<File> dxpArticles) {

		if (Validator.isNotNull(dxpArticles)) {

			List<String> currentArticlePathStrings = new ArrayList<String>();
			List<String> currentDxpArticlePathStrings = new ArrayList<String>();

			// Find DXP-only articles and convert paths to strings
			for (File articleDxp : dxpArticles) {

				String articleDxpPathString = articleDxp.getPath();
				articleDxpPathString = articleDxpPathString.replace(File.separator + "articles-dxp" + File.separator, File.separator + "articles" + File.separator);

				currentDxpArticlePathStrings.add(articleDxpPathString);
			}

			// Convert regular article paths to strings
			for (File article : articles) {

				String articlePathString = article.getPath();

				currentArticlePathStrings.add(articlePathString);
			}

			List<Integer> articleIndexes = new ArrayList<Integer>();

			// Find article indexes that should be replaced with DXP override
			for (String articleDxpPath : currentDxpArticlePathStrings) {
				int count = 0;
				for (String articlePath : currentArticlePathStrings) {

					if (articlePath.equals(articleDxpPath)) {
						articleIndexes.add(count);
					}
					count++;
				}
			}

			// Apply DXP overrides to current article list
			for (int i = 0; i < articleIndexes.size(); i++) {
				File fileOverride =articles.get(articleIndexes.get(i));
				String fileOverrideString = fileOverride.getPath();

				fileOverrideString = fileOverrideString.replace(File.separator + "articles" + File.separator, File.separator + "articles-dxp" + File.separator);
				fileOverride = new File(fileOverrideString);

				fileOverrides.add(fileOverride);

				articles.set(articleIndexes.get(i), fileOverride);
			}
		}

		return articles;
	}

	/**
	 * Returns <code>true</code> if the API URL is valid. This method is used to
	 * check URLs hosted on docs.liferay.com.
	 *
	 * @param  article the article containing the API URL
	 * @param  in the line number reader
	 * @param  line the line containing the API URL
	 * @return <code>true</code> if the API URL is valid; <code>false</code>
	 *         otherwise
	 * @throws IOException if an IO exception occurred
	 */
	private static boolean isApiUrlValid(File article, LineNumberReader in, String line)
			throws IOException {

		boolean validAPIURL = true;

		int begIndex = line.indexOf("](") + 2;
		int endIndex = line.indexOf(")", begIndex);

		String urlString = line.substring(begIndex, endIndex);

		urlString = urlString.replace("@" + platformToken + "@", platformReferenceSite);
		urlString = urlString.replace("@" + appToken + "@", appReferenceSite);

		URL url = null;

		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			// ignore this because docs.liferay.com creates many URLs that work
			// but throw the MalformedURLException
		}

		try {
			HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
			urlConnection.setRequestMethod("GET");
			urlConnection.connect() ; 
			int code = urlConnection.getResponseCode();

			if (code == 404) {
				validAPIURL = false;
			}
		} catch (NullPointerException e) {
			logInvalidUrl(article, in.getLineNumber(), line, true);
		}

		return validAPIURL;
	}

	/**
	 * Returns <code>true</code> if the LDN URL is valid. This method is used to
	 * check legacy URLs hosted on LDN.
	 *
	 * @param  url the URL to check
	 * @param  fileName the article's name
	 * @param  lineNumber the line number
	 * @return <code>true</code> if the LDN URL is valid; <code>false</code>
	 *         otherwise
	 * @throws IOException if an IO exception occurred
	 */
	private static boolean isLdnUrlValid(String url, File article, int lineNumber)
			throws IOException {

		NodeList list = new NodeList();
		boolean validLDNURL = false;

		try {
			Parser htmlParser = new Parser(url);
			list = htmlParser.extractAllNodesThatMatch(new NodeClassFilter(LinkTag.class));
		} catch (ParserException e) {
			logInvalidUrl(article, lineNumber, ldnArticle, false);
		}

		List<String> results = new LinkedList<String>();

		for (int i = 0; i < list.size(); i++) {

			LinkTag link = (LinkTag) list.elementAt(i);
			String linkString = link.getLink();
			results.add(linkString);
		}

		for (String x : results) {
			if (x.contains("2Fsearch&#x25;2Fsearch&#x26;_3_redirect&#x3d;")) {
				logInvalidUrl(article, lineNumber, ldnArticle, false);
			}
			else {
				validLDNURL = true;
			}
		}

		return validLDNURL;
	}

	/**
	 * Returns <code>true</code> if the sub-URL is valid. A sub-URL is a link
	 * to a section existing in the same article.
	 *
	 * @param  article the article containing the sub-URL
	 * @param  secondaryHeader the header ID for the section that is linked
	 * @return <code>true</code> if the sub-URL is valid; <code>false</code>
	 *         otherwise
	 * @throws IOException if an IO exception occurred
	 */
	private static boolean isSubUrlValid(File article, String secondaryHeader)
			throws IOException {

		boolean validUrl = false;
		char quotation = '"';
		LineNumberReader in = new LineNumberReader(new FileReader(article));
		String line = null;

		while ((line = in.readLine()) != null) {

			if (line.contains("[](id=" + secondaryHeader + ")")) {
				validUrl = true;
			}
			else if (line.contains("<a name=" + quotation + secondaryHeader + quotation + ">")) {
				validUrl = true;
			}
			else if (line.contains("<div") && line.contains("id=" + quotation + secondaryHeader + quotation + ">")) {
				validUrl = true;
			}
		}

		in.close();

		return validUrl;
	}

	/**
	 * Returns <code>true</code> if the URL is valid. This method is used to
	 * check the current version of documentation by matching URLs with their
	 * header IDs contained in the local repo.
	 *
	 * @param  line the line containing the URL
	 * @param  article the article containing the URL
	 * @param  in the line number reader
	 * @param  primaryHeader the primary header ID
	 * @param  secondaryHeader the secondary header ID
	 * @param  lineIndex the header's index on the line. This is useful when
	 *         there are multiple relative links on one line.
	 * @return <code>true</code> if the URL is valid; <code>false</code>
	 *         otherwise
	 * @throws IOException if an IO exception occurred
	 */
	private static boolean isUrlValid(String line, File article, LineNumberReader in,
			String primaryHeader, String secondaryHeader, int lineIndex,
			boolean differingDefaultVersion) throws IOException {

		boolean validURL = false;
		ArrayList<List<String>> headers = new ArrayList<List<String>>();

		headers = assignDirHeaders(line, lineIndex);

		// Check 7.1 portal and 1.0 commerce links from local liferay-docs repo
		if ((line.contains("/" + PORTAL_VERSION + "/") || line.contains("/" + COMMERCE_VERSION + "/")) &&
				!differingDefaultVersion) {

			// Ensure portal link versions aren't mixed with commerce link versions
			if (line.contains("/web/commerce/") && !line.contains(COMMERCE_VERSION)) {
				logInvalidUrl(article, in.getLineNumber(), line, false);
			}

			if (Validator.isNull(secondaryHeader)) {

				if (headers.get(0).contains(primaryHeader)) {
					validURL = true;
				}
			}
			else {
				if (headers.get(0).contains(primaryHeader) &&
						headers.get(1).contains(secondaryHeader)) {
					validURL = true;
				}
			}
		}

		// Check legacy URLs by checking remote LDN site. These links must be
		// published to LDN before this tool can verify them.
		else if (checkLegacyLinks && (line.contains("/" + PORTAL_VERSION_LEGACY_1 + "/") ||
				line.contains("/" + PORTAL_VERSION_LEGACY_2 + "/"))) {

			String ldnUrl = extractLdnUrl(line, in.getLineNumber(), article);
			validURL = isLdnUrlValid(ldnUrl, article, in.getLineNumber());
		}
		else {
			validURL = true;
		}

		return validURL;
	}

	/**
	 * Writes a message to the console specifying the article, line, and line
	 * number for the invalid/corrupt URL.
	 *
	 * @param article the article containing the incorrect URL
	 * @param lineNumber the line number of the line containing the incorrect
	 *        URL
	 * @param line the line containing the incorrect URL
	 * @param corruptUrlFormat whether the reported URL is caused by corrupt
	 *        formatting
	 */
	private static void logInvalidUrl(File article, int lineNumber, String line,
			boolean corruptUrlFormat) {

		String message = null;

		if (corruptUrlFormat) {
			message = "CORRUPT URL FORMATTING";
		}
		else {
			message = "INVALID URL";
		}

		resultsNumber = resultsNumber + 1;

		System.out.println(resultsNumber + ". " + "**" + message + "**\n File: " +
				article.getPath() + ":" + lineNumber + "\n" +
				" Line: " + line + "\n");

	}

	private static String appReferenceSite;
	private static String appToken;
	private static boolean checkApiLinks;
	private static boolean checkDxpLinks;
	private static boolean checkLegacyLinks;
	private static List<File> fileOverrides = new ArrayList<File>();
	private static String ldnArticle;
	private static String platformReferenceSite;
	private static String platformToken;
	private static int resultsNumber = 0;
	private static boolean validUrl;

	// Versions

	private static String COMMERCE_VERSION = "1-1";
	private static String PORTAL_VERSION = "7-2";
	private static String PORTAL_VERSION_LEGACY_1 = "7-1";
	private static String PORTAL_VERSION_LEGACY_2 = "7-0";

	// User Guide

	private static String userGuideDir = "user";
	private static List<File> userGuideArticles = new ArrayList<File>();
	private static ArrayList<List<String>> userGuideHeaders = new ArrayList<List<String>>();

	private static String userGuideReferenceDir = "discover/reference";
	private static List<File> userGuideReferenceArticles = new ArrayList<File>();
	private static ArrayList<List<String>> userGuideReferenceHeaders = new ArrayList<List<String>>();

	private static String commerceDir = "web/commerce/documentation";
	private static String commerceGithubDir = "discover/commerce";
	private static List<File> commerceArticles = new ArrayList<File>();
	private static ArrayList<List<String>> commerceHeaders = new ArrayList<List<String>>();

	private static String analyticsCloudDir = "discover/analytics-cloud";
	private static List<File> analyticsCloudArticles = new ArrayList<File>();
	private static ArrayList<List<String>> analyticsCloudHeaders = new ArrayList<List<String>>();

	// Deployment Guide

	private static String deploymentGuideDir = "deployment";
	private static List<File> deploymentGuideArticles = new ArrayList<File>();
	private static ArrayList<List<String>> deploymentGuideHeaders = new ArrayList<List<String>>();

	private static String distributeGuideDir = "distribute/publish";
	private static List<File> distributeGuideArticles = new ArrayList<File>();
	private static ArrayList<List<String>> distributeGuideHeaders = new ArrayList<List<String>>();

	// Dev Guide

	private static String appDevDir = "developer/appdev";
	private static List<File> appDevArticles = new ArrayList<File>();
	private static ArrayList<List<String>> appDevHeaders = new ArrayList<List<String>>();

	private static String commerceDevDir = "web/commerce/developer-guide";
	private static String commerceGithubDevDir = "developer/commerce";
	private static List<File> commerceDevArticles = new ArrayList<File>();
	private static ArrayList<List<String>> commerceDevHeaders = new ArrayList<List<String>>();

	private static String customizationDevDir = "developer/customization";
	private static List<File> customizationDevArticles = new ArrayList<File>();
	private static ArrayList<List<String>> customizationDevHeaders = new ArrayList<List<String>>();

	private static String frameworksDevDir = "developer/frameworks";
	private static List<File> frameworksDevArticles = new ArrayList<File>();
	private static ArrayList<List<String>> frameworksDevHeaders = new ArrayList<List<String>>();

	private static String tutorialsDir = "developer/tutorials";
	private static List<File> tutorialsArticles = new ArrayList<File>();
	private static ArrayList<List<String>> tutorialsHeaders = new ArrayList<List<String>>();

	private static String referenceDevDir = "developer/reference";
	private static List<File> referenceDevArticles = new ArrayList<File>();
	private static ArrayList<List<String>> referenceDevHeaders = new ArrayList<List<String>>();

	private static String[] articleDirs = {appDevDir, customizationDevDir, frameworksDevDir, userGuideDir,
			deploymentGuideDir, distributeGuideDir, commerceDir, analyticsCloudDir, userGuideReferenceDir,
			tutorialsDir, referenceDevDir, commerceDevDir};
}
