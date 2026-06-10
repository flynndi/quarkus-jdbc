/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.quarkiverse.jdbc.runtime.datasource.lookup;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * Stub, do-nothing DataSource implementation.
 *
 * <p>All methods throw {@link UnsupportedOperationException}.
 *
 * @author Rick Evans
 */
class StubDataSource implements DataSource {

	@Override
	public Connection getConnection() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Connection getConnection(String username, String password) {
		throw new UnsupportedOperationException();
	}

	@Override
	public PrintWriter getLogWriter() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setLogWriter(PrintWriter out) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setLoginTimeout(int seconds) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getLoginTimeout() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Logger getParentLogger() {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		throw new UnsupportedOperationException();
	}
}
