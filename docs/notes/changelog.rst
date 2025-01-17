.. _changelog:

Changelog
=========

1.4.0
-----

 - Add TLS support using OpenSSL.

 - Unrefactor socket enumerations from interfaces. It's not like we would've supported custom socket
   types properly anyway.

1.3.0
-----

 - Add pure-Kotlin IPv6 parsing.

   - The new parser is less featureful currently, but removes an external dependency.

 - IP addresses now wrap a public ``ByteString``, not a private ``ByteArray``,

 - Unrefactor the common stuff.

   - This wouldn't've worked the way I wanted.

 - Sockets entirely reworked.

   - The new ``Socket`` interface was defined, and ``SynchronousSocketStream`` was created for a
     stream over a socket.

   - Socket options now actually work.

 - File code is now common to all platforms. This should help with maintainence.

 - Add :ref:`buffer` for efficient I/O.

 - Add ``escapedString`` to ``ByteString``, and ``PurePath``.

 - Port nearly fully to Windows.

   - Only a handful of small functions don't work anymore.

1.2.0 (Released 2020-11-04)
---------------------------

 - Add ``WindowsPurePath``.

 - I/O interfaces now work similar to ``Readable|WriteableByteChannel``, reading into a buffer.

 - ``LinuxPath`` is now a ``PosixPurePath``.

 - Add compiled versions of the two static libraries for mingwX64.

 - Remove errno/winerror from OSException, and make them part of CC.

   - winerror is too broad for the design to work properly.

   - Also, I don't see really any use for errno properties that wouldn't be better served with
     more specific subclasses.

 - Add UUID support.

 - Add Windows support, using the Win32 API, for the following:

   - Filesystem paths

   - File I/O

   - Cryptographically secure psuedorandom number generations

   - Cryptography

 - Add support for non-blocking I/O on Linux.

   - The current blocking I/O functions wrap these, and will throw errors if they return in a
     non-blocking mode.

1.1.0 (Released 2020-10-14)
---------------------------

 - Add a ``SecureRandom`` API.

 - Added a cryptographic API to the core module.

    - This exposes several well tested functions for general purpose cryptographic usage.

    - Add the ``Blake2b`` integrity hashing algorithm.

    - Add the ``argon2i`` password hashing algorithm.

 - Remove the ``String``-based streams. These need a design rework.

 - Add hex-encoding for ``ByteString`` objects.

 - Add Base64 encoding for ``ByteString`` objects.

 - Add ``poll()`` based timeout for TCP sockets when connecting.

 - Add ``libtls`` based TLS support.

1.0.0
------

 - Initial release.
