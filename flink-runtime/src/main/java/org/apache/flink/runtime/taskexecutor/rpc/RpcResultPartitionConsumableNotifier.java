/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.taskexecutor.rpc;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.concurrent.ApplyFunction;
import org.apache.flink.runtime.concurrent.Future;
import org.apache.flink.runtime.io.network.partition.ResultPartitionConsumableNotifier;
import org.apache.flink.runtime.io.network.partition.ResultPartitionID;
import org.apache.flink.runtime.jobmaster.JobMasterGateway;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.runtime.taskmanager.TaskActions;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Executor;

public class RpcResultPartitionConsumableNotifier implements ResultPartitionConsumableNotifier {

	private static final Logger LOG = LoggerFactory.getLogger(RpcResultPartitionConsumableNotifier.class);

	private final UUID jobMasterLeaderId;
	private final JobMasterGateway jobMasterGateway;
	private final Executor executor;
	private final Time timeout;

	public RpcResultPartitionConsumableNotifier(
			UUID jobMasterLeaderId,
			JobMasterGateway jobMasterGateway,
			Executor executor,
			Time timeout) {
		this.jobMasterLeaderId = Preconditions.checkNotNull(jobMasterLeaderId);
		this.jobMasterGateway = Preconditions.checkNotNull(jobMasterGateway);
		this.executor = Preconditions.checkNotNull(executor);
		this.timeout = Preconditions.checkNotNull(timeout);
	}
	@Override
	public void notifyPartitionConsumable(JobID jobId, ResultPartitionID partitionId, final TaskActions taskActions) {
		Future<Acknowledge> acknowledgeFuture = jobMasterGateway.scheduleOrUpdateConsumers(
				jobMasterLeaderId, partitionId, timeout);

		acknowledgeFuture.exceptionallyAsync(new ApplyFunction<Throwable, Void>() {
			@Override
			public Void apply(Throwable value) {
				LOG.error("Could not schedule or update consumers at the JobManager.", value);

				taskActions.failExternally(new RuntimeException("Could not notify JobManager to schedule or update consumers.", value));

				return null;
			}
		}, executor);
	}
}
