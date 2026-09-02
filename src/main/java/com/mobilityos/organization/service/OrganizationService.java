package com.mobilityos.organization.service;

import com.mobilityos.common.exception.ResourceNotFoundException;
import com.mobilityos.identity.entity.User;
import com.mobilityos.identity.repository.UserRepository;
import com.mobilityos.organization.dto.CreateOrganizationRequest;
import com.mobilityos.organization.dto.OrganizationResponse;
import com.mobilityos.organization.entity.Organization;
import com.mobilityos.organization.membership.OrganizationMember;
import com.mobilityos.organization.membership.OrganizationMemberRepository;
import com.mobilityos.organization.membership.OrganizationMemberRole;
import com.mobilityos.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            UserRepository userRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrganizationResponse createOrganization(
            Long currentUserId,
            CreateOrganizationRequest request
    ) {
        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        Organization organization = new Organization(
                request.name(),
                request.type()
        );

        Organization savedOrganization =
                organizationRepository.save(organization);

        OrganizationMember ownerMembership =
                new OrganizationMember(
                        savedOrganization,
                        creator,
                        OrganizationMemberRole.OWNER
                );

        OrganizationMember savedMembership =
                organizationMemberRepository.save(ownerMembership);

        return OrganizationResponse.from(savedMembership);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> getOrganizationsForUser(
            Long currentUserId
    ) {
        return organizationMemberRepository
                .findByUserId(currentUserId)
                .stream()
                .map(OrganizationResponse::from)
                .toList();
    }
}