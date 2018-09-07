package com.tian.cloud.service.service.impl;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.tian.cloud.service.controller.response.CompanyInfo;
import com.tian.cloud.service.dao.entity.*;
import com.tian.cloud.service.dao.mapper.CommonTypeMapper;
import com.tian.cloud.service.dao.mapper.CompanyMapper;
import com.tian.cloud.service.enums.CommonTypeEnum;
import com.tian.cloud.service.enums.LineStatusEnum;
import com.tian.cloud.service.enums.Orgnization;
import com.tian.cloud.service.service.AssertsService;
import com.tian.cloud.service.service.CompanyService;
import com.tian.cloud.service.service.UserService;
import com.tian.cloud.service.util.ParamCheckUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author tianguang
 * 2018/8/25 下午9:07
 **/
@Service
@Slf4j
public class CompanyServiceImpl implements CompanyService {

    @Resource
    private CompanyMapper companyMapper;

    @Resource
    private UserService userService;

    @Resource
    private AssertsService assertsService;

    @Resource
    private CommonTypeMapper commonTypeMapper;

    @Override
    public List<Company> selectAll() {
        return companyMapper.selectAllUsable();
    }

    @Override
    public List<Company> search(String companyName) {
        return companyMapper.search(companyName);
    }

    @Override
    public CompanyInfo getCompanyInfo(int companyId) {
        Company company = companyMapper.selectById(companyId);
        List<Asserts> asserts = assertsService.getAssertsByCompany(companyId);
        List<CompanyUser> users = userService.getUserByCompany(companyId);
        if (CollectionUtils.isEmpty(users) || users.size() < 2) {
            users = defaultUsers(users, company);
        }
        List<CompanyInfo.PhoneInfo> phoneInfos = toPhoneInfos(users);
        CompanyInfo companyInfo = new CompanyInfo();
        companyInfo.setAssertsList(asserts);
        companyInfo.setCompany(company);
        companyInfo.setPhoneList(phoneInfos);
        return companyInfo;
    }

    private List<CompanyUser> defaultUsers(List<CompanyUser> userList, Company company) {
        if (CollectionUtils.isEmpty(userList)) {
            userList = Lists.newArrayList();
        }

        List<CommonType> commonTypeList = commonTypeMapper.selectUsableByType(CommonTypeEnum.POSITION.getCode());
        List<CommonType> floodTitleList = commonTypeMapper.selectUsableByType(CommonTypeEnum.FLOOD_TITLE.getCode());
        createDefaultIfEmpty(commonTypeList, CommonTypeEnum.POSITION);
        createDefaultIfEmpty(floodTitleList, CommonTypeEnum.FLOOD_TITLE);

        CommonType commonType = commonTypeList.get(0);
        CompanyUser user = new CompanyUser();
        user.setCompanyId(company.getId());
        user.setOrgCode(Orgnization.ORG1.getCode());
        user.setOrgTitle(Orgnization.ORG1.getMsg());
        user.setFloodTitle(floodTitleList.get(0).getName());
        user.setPositionId(commonType.getId());

        CompanyUser user1 = new CompanyUser();
        user1.setCompanyId(company.getId());
        user1.setOrgCode(Orgnization.ORG2.getCode());
        user1.setOrgTitle(Orgnization.ORG2.getMsg());
        user1.setPositionId(commonType.getId());
        user1.setFloodTitle(floodTitleList.get(0).getName());

        boolean containOrg1 = false;
        boolean containOrg2 = false;

        for (CompanyUser user2 : userList) {
            if (user2.getOrgCode() == Orgnization.ORG1.getCode()) {
                containOrg1 = true;
            }
            if (user2.getOrgCode() == Orgnization.ORG2.getCode()) {
                containOrg2 = true;
            }
        }

        if (!containOrg1) {
            userList.add(user);
        }
        if (!containOrg2) {
            userList.add(user1);
        }
        return userList;
    }

    private void createDefaultIfEmpty(List<CommonType> commonTypeList, CommonTypeEnum commonTypeEnum) {
        if (CollectionUtils.isEmpty(commonTypeList)) {
            commonTypeList = Lists.newArrayList();
            CommonType commonType = new CommonType();
            commonType.setStatus(LineStatusEnum.USABLE.getCode());
            commonType.setName("其他");
            commonType.setCommonTypeEnum(commonTypeEnum.getCode());
            commonType.setCreateTime(System.currentTimeMillis());
            commonType.setUpdateTime(commonType.getUpdateTime());
            commonTypeList.add(commonType);
            commonTypeMapper.saveBatch(commonTypeList);
        }
    }

    @Override
    public boolean saveCompanyInfo(CompanyInfo companyInfo) {

        this.updateCompany(companyInfo.getCompany());
        assertsService.saveOrUpdate(companyInfo.getAssertsList());
        userService.saveOrUpdate(companyInfo.getPhoneList());
        return true;
    }

    @Override
    public void saveOrUpdate(List<Company> companyList) {
        if (CollectionUtils.isEmpty(companyList)) {
            return;
        }
        List<Company> updateList = Lists.newArrayList();
        List<Company> saveList = Lists.newArrayList();
        for (Company company : companyList) {
            if (company.getId() == null || company.getId() == 0) {
                if (company.getStatus() == LineStatusEnum.USABLE.getCode()) {
                    saveList.add(company);
                }
            } else {
                company.setUpdateTime(System.currentTimeMillis());
                updateList.add(company);
            }
        }

        if (!CollectionUtils.isEmpty(updateList)) {
            for (Company update : updateList) {
                companyMapper.updateCompany(update);
            }
        }

        if (!CollectionUtils.isEmpty(saveList)) {
            companyMapper.insertBatch(saveList);
        }
    }

    @Override
    public boolean saveOrUpdate(Company company) {
        ParamCheckUtil.assertTrue(!StringUtils.isEmpty(company.getName()), "单位名称必填");
        if (company.getId() != null) {
            this.updateCompany(company);
        }
        if (company.getId() == null && company.getStatus() == LineStatusEnum.USABLE.getCode()) {
            Company dbRecord = companyMapper.selectByName(company.getName());
            ParamCheckUtil.assertTrue(dbRecord == null, "已存在相同单位名称:" + company.getName());
            companyMapper.insertBatch(Lists.newArrayList(company));
        }
        return false;
    }

    public boolean updateCompany(Company company) {

        company.setUpdateTime(System.currentTimeMillis());
        return companyMapper.updateCompany(company) > 0;
    }

    private List<CompanyInfo.PhoneInfo> toPhoneInfos(List<CompanyUser> users) {
        if (CollectionUtils.isEmpty(users)) {
            return Lists.newArrayList();
        }
        ImmutableListMultimap<Integer, CompanyUser> orgCodeMap = Multimaps.index(users, CompanyUser::getOrgCode);
        List<CompanyInfo.PhoneInfo> phoneInfos = Lists.newArrayList();
        for (Orgnization orgnization : Orgnization.values()) {
            List<CompanyUser> userList = orgCodeMap.get(orgnization.getCode());
            CompanyInfo.PhoneInfo phoneInfo = new CompanyInfo.PhoneInfo();
            phoneInfo.setId(orgnization.getCode());
            phoneInfo.setName(orgnization.getMsg());
            phoneInfo.setUserList(userList);

            phoneInfos.add(phoneInfo);
        }

        return phoneInfos;
    }
}
