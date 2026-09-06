import { ContractForm } from "../../components/ContractForm";
import { PageHeader, Section } from "../../components/Page";
import { useAppStore } from "../../store/appStore";
import { useUiStore } from "../../store/uiStore";

export function DraftPage() {
  const customers = useAppStore((state) => state.customers);
  const draftContract = useAppStore((state) => state.draftContract);
  const showToast = useUiStore((state) => state.showToast);

  return (
    <>
      <PageHeader title="起草合同" breadcrumb={["合同管理", "起草合同"]} />
      <Section title="合同基本信息" description="提交后进入待分配状态，由管理员指定会签、审批与签订人员。">
        <ContractForm
          customers={customers}
          submitText="提交起草"
          onSubmit={async (values) => {
            try {
              await draftContract(values);
              showToast("success", "起草成功，等待管理员分配人员");
            } catch (error) {
              showToast("error", error instanceof Error ? error.message : "起草失败");
            }
          }}
        />
      </Section>
    </>
  );
}
