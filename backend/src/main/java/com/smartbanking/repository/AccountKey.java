package com.smartbanking.repository;

/** Lightweight projection of an account's identity and owner. */
public record AccountKey(Long id, Long ownerUserId) {
}
