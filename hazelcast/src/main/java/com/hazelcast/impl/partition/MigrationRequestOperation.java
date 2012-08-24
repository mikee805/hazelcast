/*
 * Copyright (c) 2008-2012, Hazel Bilisim Ltd. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.impl.partition;

import com.hazelcast.core.Member;
import com.hazelcast.core.MemberLeftException;
import com.hazelcast.impl.spi.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.IOUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class MigrationRequestOperation extends Operation implements NonBlockingOperation {
    protected Address from;
    protected Address to;
    private boolean migration; // migration or copy
    private boolean diffOnly;
    private int selfCopyReplicaIndex = -1;

    public MigrationRequestOperation() {
    }

    public MigrationRequestOperation(int partitionId, Address from, Address to, int replicaIndex, boolean migration) {
        this(partitionId, from, to, replicaIndex, migration, false);
    }

    public MigrationRequestOperation(int partitionId, Address from, Address to, int replicaIndex,
                                     boolean migration, boolean diffOnly) {
        setPartitionId(partitionId).setReplicaIndex(replicaIndex);
        this.from = from;
        this.to = to;
        this.migration = migration;
        this.diffOnly = diffOnly;
    }

    public MigratingPartition createMigratingPartition() {
        return new MigratingPartition(getPartitionId(), getReplicaIndex(), from, to);
    }

    public boolean isMigration() {
        return migration;
    }

    public int getSelfCopyReplicaIndex() {
        return selfCopyReplicaIndex;
    }

    public void setSelfCopyReplicaIndex(final int selfCopyReplicaIndex) {
        this.selfCopyReplicaIndex = selfCopyReplicaIndex;
    }

    public void setFromAddress(final Address from) {
        this.from = from;
    }

    @Override
    public Operation setReplicaIndex(final int replicaIndex) {
        if (getReplicaIndex() > 0 && replicaIndex == 0) {
            System.err.println("ERROR => " + this);
            new Throwable().printStackTrace();
        }
        return super.setReplicaIndex(replicaIndex);
    }

    public void run() {
        final int partitionId = getPartitionId();
        final int replicaIndex = getReplicaIndex();
        final ResponseHandler responseHandler = getResponseHandler();
        if (to.equals(from)) {
            getLogger().log(Level.FINEST, "To and from addresses are same! => " + toString());
            responseHandler.sendResponse(Boolean.FALSE);
            return;
        }
        if (from == null) {
            getLogger().log(Level.FINEST, "From address is null => " + toString());
        }
        final PartitionManager pm = (PartitionManager) getService();
        try {
            Member target = pm.getMember(to);
            if (target == null) {
                getLogger().log(Level.WARNING, "Target member of task could not be found! => " + toString());
                responseHandler.sendResponse(Boolean.FALSE);
                return;
            }

            final NodeService nodeService = getNodeService();
            final long timeout = nodeService.getNode().groupProperties.PARTITION_MIGRATION_TIMEOUT.getLong();
            pm.lockPartition(partitionId);
            final Collection<ServiceMigrationOperation> tasks = pm.collectMigrationTasks(partitionId, replicaIndex, to,
                    diffOnly);
            nodeService.getExecutorService().execute(new Runnable() {
                public void run() {
                    try {
                        Invocation inv = nodeService.createSingleInvocation(PartitionManager.PARTITION_SERVICE_NAME,
                                new MigrationOperation(partitionId, tasks, replicaIndex, from), partitionId)
                                .setTryCount(3).setTryPauseMillis(1000).setReplicaIndex(replicaIndex).setTarget(to).build();
                        Future future = inv.invoke();
                        Boolean result = (Boolean) IOUtil.toObject(future.get(timeout, TimeUnit.SECONDS));
                        responseHandler.sendResponse(result);
                    } catch (Throwable e) {
                        onError(responseHandler, e);
                    } finally {
                        pm.unlockPartition(partitionId);
                    }
                }
            });
        } catch (Throwable e) {
            onError(responseHandler, e);
        }
    }

    private void onError(final ResponseHandler responseHandler, Throwable e) {
        Level level = Level.WARNING;
        if (e instanceof ExecutionException) {
            e = e.getCause();
        }
        if (e instanceof MemberLeftException || e instanceof IllegalStateException) {
            level = Level.FINEST;
        }
        getLogger().log(level, e.getMessage(), e);
        responseHandler.sendResponse(Boolean.FALSE);
    }

    private ILogger getLogger() {
        return getNodeService().getNode().getLogger(MigrationRequestOperation.class.getName());
    }

    public void writeInternal(DataOutput out) throws IOException {
        from.writeData(out);
        to.writeData(out);
        out.writeBoolean(migration);
        out.writeBoolean(diffOnly);
        out.writeInt(selfCopyReplicaIndex);
    }

    public void readInternal(DataInput in) throws IOException {
        from = new Address();
        from.readData(in);
        to = new Address();
        to.readData(in);
        migration = in.readBoolean();
        diffOnly = in.readBoolean();
        selfCopyReplicaIndex = in.readInt();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("MigrationRequestOperation");
        sb.append("{partitionId=").append(getPartitionId());
        sb.append(", from=").append(from);
        sb.append(", to=").append(to);
        sb.append(", replicaIndex=").append(getReplicaIndex());
        sb.append(", migration=").append(migration);
        sb.append(", diffOnly=").append(diffOnly);
        sb.append(", selfCopyReplicaIndex=").append(selfCopyReplicaIndex);
        sb.append('}');
        return sb.toString();
    }

    public Address getFromAddress() {
        return from;
    }

    public Address getToAddress() {
        return to;
    }
}
