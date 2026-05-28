import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;

const databases = new Map();
const statements = new Map();

let nextDatabaseId = 0;
let nextStatementId = 0;

function postError(id, error) {
	postMessage({'id': id, error: error instanceof Error ? error.message : String(error)});
}

function openRequest(id, requestData) {
	try {
		const newDatabaseId = nextDatabaseId++;
		const newDatabase = new sqlite3.oo1.OpfsDb(requestData.fileName);
		databases.set(newDatabaseId, newDatabase);
		postMessage({'id': id, data: {'databaseId': newDatabaseId}});
	} catch (error) {
		postError(id, error);
	}
}

function prepareRequest(id, requestData) {
	try {
		const newStatementId = nextStatementId++;
		const resultData = {
			'statementId': newStatementId,
			'parameterCount': 0,
			'columnNames': []
		};
		const database = databases.get(requestData.databaseId);
		if (!database) {
			postError(id, "Invalid database ID: " + requestData.databaseId);
			return;
		}
		const statement = database.prepare(requestData.sql);
		statements.set(newStatementId, statement);
		resultData.parameterCount = sqlite3.capi.sqlite3_bind_parameter_count(statement.pointer);
		for (let i = 0; i < statement.columnCount; i++) {
			resultData.columnNames.push(sqlite3.capi.sqlite3_column_name(statement.pointer, i));
		}
		postMessage({'id': id, data: resultData});
	} catch (error) {
		postError(id, error);
	}
}

function stepRequest(id, requestData) {
	const statement = statements.get(requestData.statementId);
	if (!statement) {
		postError(id, "Invalid statement ID: " + requestData.statementId);
		return;
	}
	try {
		const resultData = {
			'rows': [],
			'columnTypes': []
		};
		statement.reset();
		statement.clearBindings();
		for (let i = 0; i < requestData.bindings.length; i++) {
			statement.bind(i + 1, requestData.bindings[i]);
		}
		while (statement.step()) {
			if (!resultData.columnTypes.length) {
				for (let i = 0; i < statement.columnCount; i++) {
					resultData.columnTypes.push(sqlite3.capi.sqlite3_column_type(statement.pointer, i));
				}
			}
			resultData.rows.push(statement.get([]));
		}
		postMessage({'id': id, data: resultData});
	} catch (error) {
		postError(id, error);
	}
}

function closeRequest(id, requestData) {
	if (requestData.statementId !== undefined && requestData.statementId !== null) {
		const statement = statements.get(requestData.statementId);
		if (!statement) {
			postError(id, "Invalid statement ID: " + requestData.statementId);
			return;
		}
		try {
			statement.finalize();
			statements.delete(requestData.statementId);
		} catch (error) {
			postError(id, error);
			return;
		}
	}

	if (requestData.databaseId !== undefined && requestData.databaseId !== null) {
		const database = databases.get(requestData.databaseId);
		if (!database) {
			postError(id, "Invalid database ID: " + requestData.databaseId);
			return;
		}
		try {
			database.close();
			databases.delete(requestData.databaseId);
		} catch (error) {
			postError(id, error);
			return;
		}
	}
}

const commandMap = {
	'open': openRequest,
	'prepare': prepareRequest,
	'step': stepRequest,
	'close': closeRequest,
};

function handleMessage(e) {
	const requestMsg = e.data;
	if (!Object.hasOwn(requestMsg, 'data') || requestMsg.data == null) {
		postError(requestMsg.id, "Invalid request, missing 'data'.");
		return;
	}
	if (!Object.hasOwn(requestMsg.data, 'cmd') || requestMsg.data.cmd == null) {
		postError(requestMsg.id, "Invalid request, missing 'cmd'.");
		return;
	}
	const requestHandler = commandMap[requestMsg.data.cmd];
	if (requestHandler) {
		requestHandler(requestMsg.id, requestMsg.data);
	} else {
		postError(requestMsg.id, "Invalid request, unknown command: '" + requestMsg.data.cmd + "'.");
	}
}

const messageQueue = [];
onmessage = (e) => {
	if (!sqlite3) {
		messageQueue.push(e);
	} else {
		handleMessage(e);
	}
};

sqlite3InitModule().then(instance => {
	sqlite3 = instance;
	while (messageQueue.length > 0) {
		handleMessage(messageQueue.shift());
	}
}).catch(error => {
	console.error('Failed to initialize sqlite wasm worker', error);
});
