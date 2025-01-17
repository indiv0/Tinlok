/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok

// TODO: could be expanded more

/**
 * Represents information about the current operating system.
 */
public interface OsInfo {
    /** If this is a POSIX-compliant system. */
    public val isPosix: Boolean

    /** If this is Windows. */
    public val isWindows: Boolean

    /** If this is Linux. */
    public val isLinux: Boolean

    /** If this is macOS. */
    public val isMacOs: Boolean
}
