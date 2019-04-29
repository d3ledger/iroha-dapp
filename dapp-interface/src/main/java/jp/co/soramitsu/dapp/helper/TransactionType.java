package jp.co.soramitsu.dapp.helper;

public enum TransactionType {
    ADD_ASSET_QUANTITY(1),
    ADD_PEER(2),
    ADD_SIGNATORY(3),
    APPEND_ROLE(4),
    CREATE_ACCOUNT(5),
    CREATE_ASSET(6),
    CREATE_DOMAIN(7),
    CREATE_ROLE(8),
    DETACH_ROLE(9),
    GRANT_PERMISSION(10),
    REMOVE_SIGNATORY(11),
    REVOKE_PERMISSION(12),
    SET_ACCOUNT_DETAIL(13),
    SET_ACCOUNT_QUORUM(14),
    SUBTRACT_ASSET_QUANTITY(15),
    TRANSFER_ASSET(16);

    private final int index;

    TransactionType(int index) {
        this.index = index;
    }

    public static TransactionType byIndex(int index) {
        for (TransactionType m : TransactionType.values()) {
            if (m.index == index) {
                return m;
            }
        }
        return null;
    }
}

