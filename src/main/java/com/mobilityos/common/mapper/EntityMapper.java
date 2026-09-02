
package com.mobilityos.common.mapper;

public interface EntityMapper<E, D> {

    D toDto(E entity);

    E toEntity(D dto);
}