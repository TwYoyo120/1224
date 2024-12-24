package com.example.ordermanagement.service;

import com.example.ordermanagement.dto.ShippingInfoRequest;
import com.example.ordermanagement.model.Order;
import com.example.ordermanagement.model.OrderRepository;
import com.example.ordermanagement.model.ShippingInfo;
import com.example.ordermanagement.model.ShippingInfoRepository;
import com.example.ordermanagement.model.ShippingMethod;
import com.example.ordermanagement.model.ShippingMethodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class ShippingInfoService {

    @Autowired
    private ShippingInfoRepository shippingInfoRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ShippingMethodRepository shippingMethodRepository;

    /**
     * 更新物流信息，保證訂單只能有一個物流信息，支持圖片上傳及設定物流方式
     */
    public boolean updateShippingInfo(Long shippingInfoId, ShippingInfo updatedInfo, MultipartFile imageFile) {
        ShippingInfo existingInfo = shippingInfoRepository.findById(shippingInfoId)
                .orElseThrow(() -> new RuntimeException("找不到指定的物流信息"));

        validateUniqueShippingInfoForOrder(updatedInfo.getOrder().getOrderId(), shippingInfoId);

        updateBasicShippingInfo(existingInfo, updatedInfo);

        if (updatedInfo.getShippingMethod() != null && updatedInfo.getShippingMethod().getShippingMethodId() != null) {
            ShippingMethod method = findShippingMethodById(updatedInfo.getShippingMethod().getShippingMethodId());
            existingInfo.setShippingMethod(method);
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            existingInfo.setShippingInfoImage(uploadImage(imageFile));
        }

        shippingInfoRepository.save(existingInfo);
        return true;
    }

    private void validateUniqueShippingInfoForOrder(Long orderId, Long shippingInfoId) {
        Optional<ShippingInfo> existingShippingInfo = shippingInfoRepository.findByOrderOrderId(orderId);
        if (existingShippingInfo.isPresent() && !existingShippingInfo.get().getShippingInfoId().equals(shippingInfoId)) {
            throw new IllegalArgumentException("該訂單已經存在物流信息，無法重複設置。");
        }
    }

    private void updateBasicShippingInfo(ShippingInfo existingInfo, ShippingInfo updatedInfo) {
        existingInfo.setShippingInfoRecipient(updatedInfo.getShippingInfoRecipient());
        existingInfo.setShippingInfoAddress(updatedInfo.getShippingInfoAddress());
        existingInfo.setShippingInfoStatus(updatedInfo.getShippingInfoStatus());
        existingInfo.setShippingInfoTrackingNumber(updatedInfo.getShippingInfoTrackingNumber());
    }

    private byte[] uploadImage(MultipartFile imageFile) {
        if (imageFile.getContentType() == null || !imageFile.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("只支持圖片檔案格式");
        }
        try {
            return imageFile.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("圖片上傳失敗: " + e.getMessage(), e);
        }
    }


    private ShippingMethod findShippingMethodById(Long methodId) {
        return shippingMethodRepository.findById(methodId)
                .orElseThrow(() -> new RuntimeException("指定的物流方式不存在"));
    }

    /**
     * 保存物流信息（直接使用 ShippingInfo 對象）
     */
    public ShippingInfo saveShippingInfo(ShippingInfo shippingInfo) {
        validateOrderAssociation(shippingInfo.getOrder());
        return shippingInfoRepository.save(shippingInfo);
    }

    /**
     * 保存物流信息（基於 DTO 與用戶數據）
     */
    public ShippingInfo saveShippingInfoFromRequest(ShippingInfoRequest request, Long orderId) {
        ShippingInfo shippingInfo = new ShippingInfo();
        shippingInfo.setShippingInfoRecipient(request.getRecipientName());
        shippingInfo.setShippingInfoAddress(request.getAddress());
        shippingInfo.setShippingInfoStatus("Pending"); // 初始物流狀態

        ShippingMethod shippingMethod = findShippingMethodById(request.getShippingMethodId());
        shippingInfo.setShippingMethod(shippingMethod);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("找不到對應的訂單"));
        shippingInfo.setOrder(order);

        return shippingInfoRepository.save(shippingInfo);
    }

    private void validateOrderAssociation(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("物流信息必須關聯到一個訂單");
        }
    }

    /**
     * 根據物流追蹤號查詢物流信息
     */
    public Optional<ShippingInfo> getShippingInfoByTrackingNumber(String trackingNumber) {
        return shippingInfoRepository.findByShippingInfoTrackingNumber(trackingNumber);
    }

    /**
     * 根據 ID 獲取物流信息
     */
    public Optional<ShippingInfo> getShippingInfoById(Long shippingInfoId) {
        return shippingInfoRepository.findById(shippingInfoId);
    }

    /**
     * 查詢訂單與物流信息的關聯數據
     */
    public List<Map<String, Object>> getOrderShippingInfo(Long orderId, String shippingInfoStatus, String trackingNumber) {
        Specification<ShippingInfo> spec = createShippingInfoSpecification(orderId, shippingInfoStatus, trackingNumber);

        List<ShippingInfo> shippingInfos = shippingInfoRepository.findAll(spec);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ShippingInfo info : shippingInfos) {
            Map<String, Object> map = new HashMap<>();
            map.put("orderId", info.getOrder().getOrderId());
            map.put("recipient", info.getShippingInfoRecipient());
            map.put("address", info.getShippingInfoAddress());
            map.put("status", info.getShippingInfoStatus());
            map.put("trackingNumber", info.getShippingInfoTrackingNumber());
            result.add(map);
        }
        return result;
    }

    private Specification<ShippingInfo> createShippingInfoSpecification(Long orderId, String status, String trackingNumber) {
        Specification<ShippingInfo> spec = Specification.where(null);

        if (orderId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.join("order").get("orderId"), orderId));
        }
        if (status != null && !status.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("shippingInfoStatus"), status));
        }
        if (trackingNumber != null && !trackingNumber.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("shippingInfoTrackingNumber"), trackingNumber));
        }

        return spec;
    }
}

