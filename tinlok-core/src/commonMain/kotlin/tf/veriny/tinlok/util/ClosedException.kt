/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok.util

/**
 * Thrown when something is attempted on a closed resource.
 */
public class ClosedException(
    message: String? = null, cause: Throwable? = null,
) : Exception(message, cause)
