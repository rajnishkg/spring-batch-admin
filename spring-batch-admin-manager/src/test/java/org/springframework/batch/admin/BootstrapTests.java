/*
 * Copyright 2006-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.batch.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.springframework.batch.admin.web.JobController;
import org.springframework.batch.admin.web.resources.MenuManager;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.poller.DirectPoller;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author Dave Syer
 * 
 */
public class BootstrapTests {

	@Test
	public void testBootstrapConfiguration() throws Exception {
		ApplicationContext context = new ClassPathXmlApplicationContext("classpath:/META-INF/bootstrap/**/*.xml");
		assertTrue(context.containsBean("jobRepository"));
	}

	@Test
	public void testWebappRootConfiguration() throws Exception {
		ApplicationContext context = new ClassPathXmlApplicationContext(
				"classpath:/org/springframework/batch/admin/web/resources/webapp-config.xml");
		assertTrue(context.containsBean("jobRepository"));
	}

	@Test
	public void testServletConfiguration() throws Exception {
		ClassPathXmlApplicationContext parent = new ClassPathXmlApplicationContext(
				"classpath:/org/springframework/batch/admin/web/resources/webapp-config.xml");
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				new String[] { "classpath:/org/springframework/batch/admin/web/resources/servlet-config.xml" }, parent);

		assertTrue(context.containsBean("jobRepository"));
		String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context.getBeanFactory(),
				JobController.class);
		assertEquals(1, beanNames.length);

		MenuManager menuManager = context.getBean(MenuManager.class);
		assertEquals(4, menuManager.getMenus().size());

		context.refresh();

		ClassPathXmlApplicationContext child = new ClassPathXmlApplicationContext(
				new String[] { "classpath:/test-job-context.xml" }, parent);
		Job job = child.getBean(Job.class);
		final JobExecution jobExecution = parent.getBean(JobLauncher.class).run(job, new JobParameters());

		new DirectPoller<BatchStatus>(100).poll(new Callable<BatchStatus>() {
			public BatchStatus call() throws Exception {
				BatchStatus status = jobExecution.getStatus();
				if (status.isLessThan(BatchStatus.STOPPED) && status!=BatchStatus.COMPLETED) {
					return null;
				}
				return status;
			}
		}).get(2000, TimeUnit.MILLISECONDS);

		child.close();
		context.close();
		parent.close();

		assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());

	}

}
