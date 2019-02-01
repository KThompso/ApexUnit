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

import com.sforce.cd.apexUnit.ApexUnitUtils;
import com.sforce.cd.apexUnit.arguments.CommandLineArguments;
import com.sforce.cd.apexUnit.client.QueryConstructor;
import com.sforce.cd.apexUnit.client.codeCoverage.WebServiceInvoker;
import com.sforce.cd.apexUnit.client.connection.ConnectionHandler;
import com.sforce.cd.apexUnit.client.utils.ApexClassFetcherUtils;
import com.sforce.cd.apexUnit.model.ApexClass;
import com.sforce.cd.apexUnit.model.TestExecutionRequest;
import com.sforce.cd.apexUnit.model.TestExecutionRequest.TestExecutionRequestBuilder;
import com.sforce.cd.apexUnit.report.ApexReportBean;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestExecutor {
	private static Logger LOG = LoggerFactory.getLogger(TestExecutor.class);

	public ApexReportBean[] testExecutionFlow() {

		ConnectionHandler connectionHandler = ConnectionHandler.getConnectionHandlerInstance();
		PartnerConnection conn = connectionHandler.getConnection();
		String parentJobId;
		ArrayList<ApexReportBean> apexReportBean = null;
		ApexReportBean[] apexReportBeanArray = null;

		if (conn == null) {
			ApexUnitUtils.shutDownWithErrMsg("Unable to establish Connection with the org. Suspending the run..");
		}

		LOG.info("############################# Now executing - Apex tests.. #############################");

		Collection<String> sourceClassIds = new ArrayList<String>();
		Collection<String> testClassIds = new ArrayList<String>();
		TestExecutionRequestBuilder requestBuilder = TestExecutionRequest.builder();

		for (ApexClass apexClass : ApexClassFetcherUtils.getAllApexClasses()) {
			if (apexClass.isTestClass()) {
				testClassIds.add(apexClass.Id);
				requestBuilder.classid(apexClass.Id);
			} else {
				sourceClassIds.add(apexClass.Id);
			}
		}

		String soql = QueryConstructor.getQueryForApexClassInfo(
				processClassArrayForQuery(testClassIds.toArray(new String[testClassIds.size()])));

		QueryResult queryresult = null;
		try {
			queryresult = conn.query(soql);
		} catch (ConnectionException e) {
			LOG.debug(e.getMessage());
		}

		SObject[] s = queryresult.getRecords();
		SObject[] updateResult = new SObject[s.length];
		int i = 0;
		for (SObject sObject : s) {
			SObject obj = new SObject();
			obj.setType("ApexTestQueueItem");
			obj.setId(sObject.getId());
			obj.setField("status", "Aborted");
			updateResult[i++] = obj;
		}
		LOG.info("No of test classes running tests " + queryresult.getSize());
		boolean submitTest = true;
		if (queryresult.getSize() != 0) {
			LOG.info("Test Reload " + CommandLineArguments.isTestReload());
			if (CommandLineArguments.isTestReload()) {

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
		} else if (testClassIds != null && testClassIds.size() > 0) {
			parentJobId = WebServiceInvoker.runTestsAsync(requestBuilder.build());
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
