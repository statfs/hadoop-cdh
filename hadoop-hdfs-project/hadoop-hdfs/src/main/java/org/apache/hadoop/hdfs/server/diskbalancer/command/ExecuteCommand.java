/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.hadoop.hdfs.server.diskbalancer.command;

import com.google.common.base.Preconditions;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.hdfs.protocol.ClientDatanodeProtocol;
import org.apache.hadoop.hdfs.server.diskbalancer.DiskBalancerException;
import org.apache.hadoop.hdfs.tools.DiskBalancer;
import org.apache.hadoop.hdfs.server.diskbalancer.planner.NodePlan;
import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;


/**
 * executes a given plan.
 */
public class ExecuteCommand extends Command {

  /**
   * Constructs ExecuteCommand.
   *
   * @param conf - Configuration.
   */
  public ExecuteCommand(Configuration conf) {
    super(conf);
    addValidCommandParameters(DiskBalancer.EXECUTE, "Executes a given plan.");
    addValidCommandParameters(DiskBalancer.NODE, "Name of the target node.");
  }

  /**
   * Executes the Client Calls.
   *
   * @param cmd - CommandLine
   */
  @Override
  public void execute(CommandLine cmd) throws Exception {
    LOG.info("Executing \"execute plan\" command");
    Preconditions.checkState(cmd.hasOption(DiskBalancer.EXECUTE));
    verifyCommandOptions(DiskBalancer.EXECUTE, cmd);

    String planFile = cmd.getOptionValue(DiskBalancer.EXECUTE);
    Preconditions.checkArgument(planFile != null && !planFile.isEmpty(),
        "Invalid plan file specified.");

    String planData = null;
    try (FSDataInputStream plan = open(planFile)) {
      planData = IOUtils.toString(plan);
    }
    submitPlan(planData);
  }

  /**
   * Submits plan to a given data node.
   *
   * @param planData - PlanData Json String.
   * @throws IOException
   */
  private void submitPlan(String planData) throws IOException {
    Preconditions.checkNotNull(planData);
    NodePlan plan = readPlan(planData);
    String dataNodeAddress = plan.getNodeName() + ":" + plan.getPort();
    Preconditions.checkNotNull(dataNodeAddress);
    ClientDatanodeProtocol dataNode = getDataNodeProxy(dataNodeAddress);
    String planHash = DigestUtils.sha512Hex(planData);
    try {
      dataNode.submitDiskBalancerPlan(planHash, DiskBalancer.PLAN_VERSION,
          planData, false); // TODO : Support skipping date check.
    } catch (DiskBalancerException ex) {
      LOG.error("Submitting plan on  {} failed. Result: {}, Message: {}",
          plan.getNodeName(), ex.getResult().toString(), ex.getMessage());
      throw ex;
    }
  }

  /**
   * Returns a plan from the Json Data.
   *
   * @param planData - Json String
   * @return NodePlan
   * @throws IOException
   */
  private NodePlan readPlan(String planData) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(planData, NodePlan.class);
  }

  /**
   * Gets extended help for this command.
   *
   * @return Help Message
   */
  @Override
  protected String getHelp() {
    return "Execute command takes a plan and runs it against the node. e.g. " +
        "hdfs diskbalancer -execute <nodename.plan.json> ";
  }
}
