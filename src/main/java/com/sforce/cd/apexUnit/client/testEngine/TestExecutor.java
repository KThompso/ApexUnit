/* 
 * Copyright (c) 2016, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

/*
 * Class for controlling the test execution flow in APexUnit
 * @author adarsh.ramakrishna@salesforce.com
 */ 
 

package com.sforce.cd.apexUnit.client.testEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sforce.async.BulkConnection;
import com.sforce.cd.apexUnit.ApexUnitUtils;
import com.sforce.cd.apexUnit.arguments.CommandLineArguments;
import com.sforce.cd.apexUnit.client.QueryConstructor;
import com.sforce.cd.apexUnit.client.codeCoverage.OAuthTokenGenerator;
import com.sforce.cd.apexUnit.client.codeCoverage.WebServiceInvoker;
import com.sforce.cd.apexUnit.client.connection.ConnectionHandler;
import com.sforce.cd.apexUnit.client.utils.ApexClassFetcherUtils;
import com.sforce.cd.apexUnit.model.ApexClass;
import com.sforce.cd.apexUnit.report.ApexReportBean;
import com.sforce.soap.partner.LoginResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import com.sforce.ws.transport.SoapConnection;

public class TestExecutor {
	private static Logger LOG = LoggerFactory.getLogger(TestExecutor.class);
	public class TestExecutionRequest {
		public Collection<TestClass> tests = new ArrayList<TestClass>();
	}
	
	public class TestClass {
		public TestClass(String classId) {
			this.classId = classId;
		}
		
		public String classId;
	}
	
	// Returns job id?
	public String executeTests(Collection<String> classNames) {
		
		PostMethod post = null;
		HttpClient httpclient = new HttpClient();

		try {
			// the client id and secret is applicable across all dev orgs
			String authorizationServerURL = CommandLineArguments.getOrgUrl() + "/services/data/v43.0/tooling/runTestsAsynchronous/";
			httpclient.getParams().setSoTimeout(0);
			post = new PostMethod(authorizationServerURL);
			
			post.addRequestHeader("Accept", "application/json");
			post.addRequestHeader("Authorization", "OAuth " + OAuthTokenGenerator.getOrgToken());
			
			Gson gson = new Gson();
			
			TestExecutionRequest req = new TestExecutionRequest();
			for (String name : classNames) {
				req.tests.add(new TestClass(name));
			}
			String requestString = gson.toJson(req);
			post.setRequestEntity(new StringRequestEntity(requestString, "application/json", "UTF-8"));
			httpclient.executeMethod(post);

			return post.getResponseBodyAsString().replace("\"", "");

		} catch (Exception ex) {
			ApexUnitUtils.shutDownWithDebugLog(ex, "Exception during post method: " + ex);
			if(LOG.isDebugEnabled()) {
				ex.printStackTrace();
			}
		} finally {
			post.releaseConnection();
		}
		return null;
		
	}

	public ApexReportBean[] testExecutionFlow() {

		ConnectionHandler connectionHandler = ConnectionHandler.getConnectionHandlerInstance();
		PartnerConnection conn = connectionHandler.getConnection();
		String parentJobId;
		ArrayList<ApexReportBean> apexReportBean = null;
		ApexReportBean[] apexReportBeanArray = null;
		

		if (conn == null) {
			ApexUnitUtils.shutDownWithErrMsg("Unable to establish Connection with the org. Suspending the run..");
		}
		
		LOG.info(
				"############################# Now executing - Apex tests.. #############################");
		
		Collection<String> sourceClasses = new ArrayList<String>();
		Collection<String> testClasses = new ArrayList<String>();
		Collection<ApexClass> classes = ApexClassFetcherUtils.getApexClasses();
		
		for (ApexClass ac : classes) {
			if (ac.testClass) {
				testClasses.add(ac.Id);
			} else {
				sourceClasses.add(ac.Id);
			}
		}
		
		String[] testClassArray = new String[testClasses.size()];
		int ix = 0;
		for (String s : testClasses) {
			testClassArray[ix++] = s;
		}
		
		if (LOG.isDebugEnabled()) {
			ApexClassFetcherUtils.logTheFetchedApexClasses(testClassArray);
		}
		
		String soql = QueryConstructor.getQueryForApexClassInfo(processClassArrayForQuery(testClassArray));
		QueryResult queryresult = null;
		try {
			queryresult = conn.query(soql);
		} catch (ConnectionException e) {
			
			LOG.debug(e.getMessage());
		}
		SObject []s= queryresult.getRecords();
		SObject[] updateResult = new SObject[s.length];
		int i =0;
		for (SObject sObject : s) {
			SObject obj = new SObject();
			obj.setType("ApexTestQueueItem");
			obj.setId(sObject.getId());
			obj.setField("status", "Aborted");
			updateResult[i++] = obj;
		}
		LOG.info("No of test classes running tests "+queryresult.getSize());
		boolean submitTest = true;
		if(queryresult.getSize() != 0){
			LOG.info("Test Reload "+ CommandLineArguments.isTestReload());
			if(CommandLineArguments.isTestReload()){
				
				try {
					conn.update(updateResult);
				} catch (ConnectionException e) {
					LOG.debug(e.getMessage());
				}
			} else {
				submitTest = false;
			}
		}

		if (!submitTest) {
			ApexUnitUtils.shutDownWithErrMsg("Test for these classes already running/enqueue at server...");
		} else if (testClasses != null && testClasses.size() > 0) {
			parentJobId = executeTests(testClasses);
			apexReportBean = new ArrayList<ApexReportBean>();
			LOG.info("#####Parent JOB ID  #####" + parentJobId);
			if (parentJobId != null) {
				LOG.info("Parent job ID for the submission of the test classes to the Force.com platform is: "
						+ parentJobId);
				TestStatusPollerAndResultHandler queryPollerAndResultHandler = new TestStatusPollerAndResultHandler();
				LOG.info("############################# Now executing - Apex tests.. #############################");
				apexReportBeanArray = queryPollerAndResultHandler.fetchResultsFromParentJobId(parentJobId, conn);
				if (apexReportBeanArray != null) {
					apexReportBean.addAll(Arrays.asList(apexReportBeanArray));
				}

			}

		}

		return apexReportBean.toArray(new ApexReportBean[0]);

	}
	
	private String processClassArrayForQuery(String[] classesAsArray) {
		String queryString = "";
		for (int i = 0; i < classesAsArray.length; i++) {
			queryString += "'" + classesAsArray[i] + "'";
			queryString += ",";
		}
		if (queryString.length() > 1) {
			queryString = queryString.substring(0, queryString.length() - 1);
		}
		return queryString;
	}

}
