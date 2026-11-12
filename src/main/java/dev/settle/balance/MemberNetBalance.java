package dev.settle.balance;

import java.util.UUID;

public interface MemberNetBalance {

    UUID getUserId();

    String getCurrency();

    long getBalanceMinor();
}
