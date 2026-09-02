package com.mobilityos.organization.service;

import com.mobilityos.identity.entity.User;
import com.mobilityos.identity.repository.UserRepository;
import com.mobilityos.organization.dto.CreateOrganizationRequest;
import com.mobilityos.organization.dto.OrganizationResponse;
import com.mobilityos.organization.entity.Organization;
import com.mobilityos.organization.membership.OrganizationMember;
import com.mobilityos.organization.membership.OrganizationMemberRepository;
import com.mobilityos.organization.membership.OrganizationMemberRole;
import com.mobilityos.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrganizationServiceTest {

    private OrganizationRepository organizationRepository;
    private OrganizationMemberRepository organizationMemberRepository;
    private UserRepository userRepository;

    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {

        organizationRepository = mock(OrganizationRepository.class);

        organizationMemberRepository =
                mock(OrganizationMemberRepository.class);

        userRepository = mock(UserRepository.class);

        organizationService = new OrganizationService(
                organizationRepository,
                organizationMemberRepository,
                userRepository
        );
    }

    @Test
    void creatorBecomesOwnerOfNewOrganization() {

        User user = new User(
                "0911000000",
                "hashed-password"
        );

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(organizationMemberRepository.save(
                any(OrganizationMember.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        CreateOrganizationRequest request =
                new CreateOrganizationRequest(
                        "Abebe Transport",
                        Organization.OrganizationType.PRIVATE_OWNER
                );

        OrganizationResponse response =
                organizationService.createOrganization(1L, request);

        ArgumentCaptor<OrganizationMember> membershipCaptor =
                ArgumentCaptor.forClass(OrganizationMember.class);

        verify(organizationMemberRepository)
                .save(membershipCaptor.capture());

        OrganizationMember membership =
                membershipCaptor.getValue();

        assertEquals(
                OrganizationMemberRole.OWNER,
                membership.getRole()
        );

        assertSame(
                user,
                membership.getUser()
        );

        assertEquals(
                "Abebe Transport",
                membership.getOrganization().getName()
        );

        assertEquals(
                OrganizationMemberRole.OWNER,
                response.membershipRole()
        );
    }
}