package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.BackendConfig

/** Where the mocked backend "is"; the engine never connects anywhere. */
val TestBackend = BackendConfig("https://example.invalid/", "test-token")
