package com.lurremcfly.yogaalarm.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingUiStateTest {
    private val ready = BillingUiState(
        connected = true,
        loading = false,
        loadingOffers = false,
        catalogSettled = true,
        ownershipResolution = OwnershipResolution.RESOLVED,
    )

    @Test fun confirmedFreeOwnershipCanPurchaseAfterCatalogSettles() {
        assertTrue(ready.canPurchase)
    }

    @Test fun initialOrUnavailableOwnershipCannotPurchase() {
        assertFalse(BillingUiState().canPurchase)
        assertFalse(ready.copy(ownershipResolution = OwnershipResolution.LOADING).canPurchase)
        assertFalse(ready.copy(ownershipResolution = OwnershipResolution.UNAVAILABLE).canPurchase)
    }

    @Test fun suspendedVerificationAndPendingPaymentBlockCompetingCheckout() {
        assertFalse(ready.copy(verifyingPurchases = true).canPurchase)
        assertFalse(ready.copy(pendingPurchase = true).canPurchase)
        assertFalse(ready.copy(purchasing = true).canPurchase)
    }

    @Test fun offlinePaidExpiryAndPendingOwnershipDeferTrialConversion() {
        assertTrue(ready.copy(offlineAccessExpired = true).deferTrialConversion)
        assertTrue(ready.copy(pendingPurchase = true).deferTrialConversion)
        assertTrue(ready.copy(verifyingPurchases = true).deferTrialConversion)
        assertTrue(ready.copy(purchasing = true).deferTrialConversion)
        assertTrue(BillingUiState().deferTrialConversion)
        assertTrue(ready.copy(ownershipResolution = OwnershipResolution.UNAVAILABLE).deferTrialConversion)
        assertFalse(ready.deferTrialConversion)
    }

    @Test fun incompleteCatalogRefreshAndDisconnectedStoreCannotPurchase() {
        assertFalse(ready.copy(catalogSettled = false).canPurchase)
        assertFalse(ready.copy(loadingOffers = true).canPurchase)
        assertFalse(ready.copy(connected = false).canPurchase)
        assertFalse(ready.copy(loading = true).canPurchase)
    }

    @Test fun paidOwnersCannotPurchaseAnotherPlan() {
        ProEntitlement.entries.forEach { entitlement ->
            assertFalse(ready.copy(entitlement = entitlement).canPurchase)
        }
    }
}
