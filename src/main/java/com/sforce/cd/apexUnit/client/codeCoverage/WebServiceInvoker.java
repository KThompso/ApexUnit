/* 
 * Copyright (c) 2016, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

/*
 * Class to invoke web services calls: get and post methods for the REST APIs using OAUTH
 * 
 * @author adarsh.ramakrishna@salesforce.com
 */

package com.sforce.cd.apexUnit.client.codeCoverage;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.httpclient.HttpClient;
import com.sforce.cd.apexUnit.model.TestExecutionRequest;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sforce.cd.apexUnit.ApexUnitUtils;
import com.sforce.cd.apexUnit.arguments.CommandLineArguments;

import static java.net.URLEncoder.encode;

/*
 * WebServiceInvoker provides interfaces for get and post methods for the REST APIs using OAUTH
 */
public class WebServiceInvoker {
	private static Logger LOG = LoggerFactory.getLogger(WebServiceInvoker.class);
	public static final String API_VERSION = "44.0";

	/*
	 * Utility to perform HTTP post operation on the orgUrl with the specific
	 * sub-url as mentioned in the relativeServiceURL
	 * 
	 * @param relativeServiceURL - relative service url w.r.t org url for firing
	 * post request
	 * 
	 * @return : hashmap with key-value pairs of response from the post query
	 */
	public HashMap<String, String> doPost(String relativeServiceURL) {
		Header[] headers = new Header[] { new Header("Content-Type", "application/x-www-form-urlencoded"),
				new Header("X-PrettyPrint", "1") };

		String json = doPost(relativeServiceURL, generateRequestString(), "application/x-www-form-urlencoded", "UTF-8",
				headers);
		
		Gson gson = new Gson();
		// obtain the result map from the response body and get the access token
		return gson.fromJson(json, new TypeToken<HashMap<String, String>>() {
		}.getType());
	}

	public static String doPost(String relativeServiceURL, String body, String contentType, String encoding,
			Header[] headers) {
		PostMethod post = null;
		HttpClient httpclient = new HttpClient();
			String authorizationServerURL = CommandLineArguments.getOrgUrl() + relativeServiceURL;
			httpclient.getParams().setSoTimeout(0);

		try {
			// Set proxy if needed
			if (CommandLineArguments.getProxyHost() != null && CommandLineArguments.getProxyPort() != null) {
				LOG.debug("Setting proxy configuraiton to " + CommandLineArguments.getProxyHost() + " on port "
						+ CommandLineArguments.getProxyPort());
				HostConfiguration hostConfiguration = httpclient.getHostConfiguration();
				hostConfiguration.setProxy(CommandLineArguments.getProxyHost(), CommandLineArguments.getProxyPort());
				httpclient.setHostConfiguration(hostConfiguration);
			}

			post = new PostMethod(authorizationServerURL);

			if (headers != null) {
				for (Header header : headers)
					post.addRequestHeader(header);
			}

			post.setRequestEntity(new StringRequestEntity(body, contentType, encoding));
			httpclient.executeMethod(post);
			return post.getResponseBodyAsString();
		} catch (Exception ex) {
			ApexUnitUtils.shutDownWithDebugLog(ex, ex.getMessage());
			if (LOG.isDebugEnabled()) {
				ex.printStackTrace();
			}
		} finally {
			post.releaseConnection();
		}
		return null;
	}

	// Returns job id?
	public static String runTestsAsync(TestExecutionRequest testExecutionRequest) {
		String relativeServiceUrl = "/services/data/v" + API_VERSION + "/tooling/runTestsAsynchronous/";

		Header[] headers = new Header[] { new Header("Accept", "application/json"),
				new Header("Authorization", "OAuth " + OAuthTokenGenerator.getOrgToken()) };

		String requestString = testExecutionRequest.toJson();

		// TODO handle bad response.
		String json = doPost(relativeServiceUrl, requestString, "application/json", "UTF-8", headers);
		return json.replace("\"", "");
	}

	public static String generateRequestString() {
		String requestString = "";
		try {
			requestString = "grant_type=password&client_id=" + CommandLineArguments.getClientId() + "&client_secret="
					+ CommandLineArguments.getClientSecret() + "&username=" + CommandLineArguments.getUsername()
					+ "&password=" + encode(CommandLineArguments.getPassword(), "UTF-8");
		} catch (UnsupportedEncodingException ex) {
			ApexUnitUtils.shutDownWithDebugLog(ex, "Exception during request string generation: " + ex);
			if (LOG.isDebugEnabled()) {
				ex.printStackTrace();
			}
		}

		return requestString;
	}

	public static JSONObject doGet(String relativeServiceURL, String soql, String accessToken) {
		if (soql != null && !soql.equals("")) {
			try {
				relativeServiceURL += "/query/?q=" + encode(soql, "UTF-8");
			} catch (UnsupportedEncodingException e) {

				ApexUnitUtils.shutDownWithDebugLog(e,
						"Error encountered while trying to encode the query string using UTF-8 format. The error says: "
								+ e.getMessage());
			}
		}
		return (JSONObject) JSONValue.parse(doGet(relativeServiceURL, accessToken));
	}

	/*
	 * method to perform get operation using the access token for the org and return
	 * the json response
	 * 
	 * @param relativeServiceURL - relative service url w.r.t org url for firing
	 * post request
	 * 
	 * @param accessToken : access token for the org(generated in the post method)
	 * 
	 * @return : json response from the get request
	 */
	public static String doGet(String relativeServiceURL, String accessToken) {

		LOG.debug("relativeServiceURL in doGet method:" + relativeServiceURL);
		HttpClient httpclient = new HttpClient();
		// Set proxy if needed
		if (CommandLineArguments.getProxyHost() != null && CommandLineArguments.getProxyPort() != null) {
			LOG.debug("Setting proxy configuraiton to " + CommandLineArguments.getProxyHost() + " on port "
				+ CommandLineArguments.getProxyPort());
			HostConfiguration hostConfiguration = httpclient.getHostConfiguration();
			hostConfiguration.setProxy(CommandLineArguments.getProxyHost(), CommandLineArguments.getProxyPort());
			httpclient.setHostConfiguration(hostConfiguration);
		}
		GetMethod get = null;

		String authorizationServerURL = CommandLineArguments.getOrgUrl() + relativeServiceURL;
		get = new GetMethod(authorizationServerURL);
		get.addRequestHeader("Content-Type", "application/json");
		get.setRequestHeader("Authorization", "Bearer " + accessToken);
		LOG.debug("Start GET operation for the url..." + authorizationServerURL);

							try {
			httpclient.executeMethod(get);
			return get.getResponseBodyAsString();
		} catch (HttpException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
				}

		return null;
	}

	/**
	 * 
	 * execute the HTTP Get method and return response as Input stream
	 * 
	 * @param httpclient HTTPClient
	 * @param get        GetMethod
	 * @return
	 * @throws IOException
	 * @throws HttpException
	 */

	private static InputStream executeHTTPMethod(HttpClient httpclient, GetMethod get, String authorizationServerURL) {
		try {
			httpclient.executeMethod(get);
		} catch (HttpException e) {
			ApexUnitUtils.shutDownWithDebugLog(e,
					"Encountered HTTP exception when executing get method using OAuth authentication for the url "
							+ authorizationServerURL + ". The error says: " + e.getMessage());
		} catch (IOException e) {
			ApexUnitUtils.shutDownWithDebugLog(e,
					"Encountered IO exception when executing get method using OAuth authentication for the url "
							+ authorizationServerURL + ". The error says: " + e.getMessage());
		}
		LOG.info("Status code : " + get.getStatusCode() + "   Status message from the get request:"
				+ get.getStatusText() + " Reason phrase: " + get.getStatusLine().getReasonPhrase());
		
		InputStream instream = null;
		try {
			// don't delete the below line --i.e. getting response body as
			// string. Getting response as stream fails upon deleting the below
			// line! strange!
			String respStr;
			respStr = get.getResponseBodyAsString();
			instream = get.getResponseBodyAsStream();
		} catch (IOException e) {

			ApexUnitUtils.shutDownWithDebugLog(e,
					"Encountered IO exception when obtaining response body for the get method. The error says: "
							+ e.getMessage());
		}
		return instream;
	}

	/*
	 * Method to cast a collection of objects to a set of given type
	 * 
	 * @param clazz - class type of the collection
	 * 
	 * @param c - collection set
	 * 
	 * @return set - A set returned that is of the class 'clazz'
	 */
	public static <T> Set<T> castSet(Class<? extends T> clazz, Collection<?> c) {
		Set<T> set = new HashSet<T>();
		for (Object o : c)
			set.add(clazz.cast(o));
		return set;
	}
}
