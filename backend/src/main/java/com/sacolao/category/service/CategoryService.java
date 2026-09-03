package com.sacolao.category.service;

import com.sacolao.category.dto.CategoryResponse;
import com.sacolao.category.dto.CreateCategoryRequest;
import com.sacolao.category.dto.UpdateCategoryRequest;
import com.sacolao.category.entity.Category;
import com.sacolao.category.mapper.CategoryMapper;
import com.sacolao.category.repository.CategoryRepository;
import com.sacolao.common.exception.ConflictException;
import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.product.repository.ProductRepository;
import com.sacolao.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final EstablishmentRepository establishmentRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            EstablishmentRepository establishmentRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.establishmentRepository = establishmentRepository;
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        UUID tenantId = TenantContext.require();
        String name = request.name().trim();
        if (categoryRepository.existsByEstablishment_IdAndNameIgnoreCase(tenantId, name)) {
            throw new ConflictException("CATEGORY_NAME_EXISTS", "Já existe uma categoria com este nome");
        }
        Establishment establishment = establishmentRepository.findById(tenantId)
                .orElseThrow(this::notFound);
        Category category = new Category();
        category.setEstablishment(establishment);
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        category.setImageUrl(blankToNull(request.imageUrl()));
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setActive(request.active() == null || request.active());
        return CategoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findByEstablishment_IdOrderBySortOrderAscNameAsc(TenantContext.require())
                .stream()
                .map(CategoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return CategoryMapper.toResponse(requireInTenant(id));
    }

    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        Category category = requireInTenant(id);
        UUID tenantId = TenantContext.require();
        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            if (categoryRepository.existsByEstablishment_IdAndNameIgnoreCaseAndIdNot(tenantId, name, id)) {
                throw new ConflictException("CATEGORY_NAME_EXISTS", "Já existe uma categoria com este nome");
            }
            category.setName(name);
        }
        if (request.description() != null) {
            category.setDescription(blankToNull(request.description()));
        }
        if (request.imageUrl() != null) {
            category.setImageUrl(blankToNull(request.imageUrl()));
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return CategoryMapper.toResponse(category);
    }

    @Transactional
    public void delete(UUID id) {
        Category category = requireInTenant(id);
        if (productRepository.existsByCategory_IdAndEstablishment_Id(id, TenantContext.require())) {
            throw new UnprocessableException(
                    "CATEGORY_IN_USE",
                    "Não é possível excluir uma categoria que ainda tem produtos"
            );
        }
        categoryRepository.delete(category);
    }

    public Category requireInTenant(UUID id) {
        return categoryRepository.findByIdAndEstablishment_Id(id, TenantContext.require())
                .orElseThrow(this::notFound);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Recurso não encontrado");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
