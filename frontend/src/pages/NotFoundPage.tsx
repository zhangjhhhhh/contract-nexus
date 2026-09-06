import { FileQuestion } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "../components/Button";

export function NotFoundPage() {
  return (
    <div className="grid min-h-[calc(100vh-128px)] place-items-center">
      <div className="text-center">
        <FileQuestion className="mx-auto h-12 w-12 text-primary" />
        <h1 className="mb-2 mt-5 text-title font-semibold text-text">页面不存在</h1>
        <p className="mb-6 text-body text-muted">请检查地址，或返回工作台继续操作。</p>
        <Link to="/dashboard"><Button variant="secondary">返回工作台</Button></Link>
      </div>
    </div>
  );
}
