/*
 * Copyright (C) 2020-2021 Lura Skye Revuwution.
 *
 * This file is part of Tinlok.
 *
 * Tinlok is dually released under the GNU Lesser General Public License,
 * Version 3 or later, or the Mozilla Public License 2.0.
 */

package tf.veriny.tinlok.io

/**
 * Represents any stream that operates in both directions (it is both readable and writeable).
 */
public interface BidirectionalStream : ReadWrite, ReadableStream, WriteableStream
