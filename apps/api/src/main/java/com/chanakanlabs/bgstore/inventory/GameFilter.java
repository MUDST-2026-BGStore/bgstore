package com.chanakanlabs.bgstore.inventory;

import com.chanakanlabs.bgstore.contract.model.GameAvailability;
import com.chanakanlabs.bgstore.contract.model.GameCategory;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** The inventory list query. Null members mean "do not narrow on this". */
record GameFilter(
    @Nullable UUID branchId,
    @Nullable GameCategory category,
    @Nullable GameAvailability status,
    @Nullable String search,
    int page,
    int size) {}
