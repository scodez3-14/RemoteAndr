package com.remotephone.executor

import com.remotephone.data.model.Command
import com.remotephone.data.model.CommandResult

/**
 * Interface for command executors. Each executor handles a specific command type
 * and returns a result.
 */
interface CommandExecutor {
    /**
     * Execute the given command and return a result.
     * @param command The command to execute
     * @return The result of the command execution
     */
    suspend fun execute(command: Command): CommandResult
}
