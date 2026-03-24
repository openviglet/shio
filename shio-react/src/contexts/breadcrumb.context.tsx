
import { createContext, type Dispatch, type ReactNode, type SetStateAction, useCallback, useContext, useMemo, useState } from "react";
export type BreadcrumbItem = {
    label: string;
    href?: string;
};

interface BreadcrumbContextType {
    items: BreadcrumbItem[];
    setItems: Dispatch<SetStateAction<BreadcrumbItem[]>>;
    pushItem: (item: BreadcrumbItem) => void;
    popItem: () => void;
    resetBreadcrumb: (initialItems?: BreadcrumbItem[]) => void;
}

const BreadcrumbContext = createContext<BreadcrumbContextType | undefined>(undefined);
export function BreadcrumbProvider({ children }: { readonly children: ReactNode }) {
    const [items, setItems] = useState<BreadcrumbItem[]>([]);

    const pushItem = useCallback((newItem: BreadcrumbItem) => {
        setItems((prev) => {
            if (prev.length > 0 && prev[prev.length - 1].label === newItem.label) {
                return prev;
            }
            return [...prev, newItem];
        });
    }, []);

    const popItem = useCallback(() => {
        setItems((prev) => prev.slice(0, -1));
    }, []);

    const resetBreadcrumb = useCallback((initialItems: BreadcrumbItem[] = []) => {
        setItems(initialItems);
    }, []);

    const contextValue = useMemo(
        () => ({
            items,
            setItems,
            pushItem,
            popItem,
            resetBreadcrumb,
        }),
        [items, pushItem, popItem, resetBreadcrumb]
    );

    return (
        <BreadcrumbContext.Provider value={contextValue}>
            {children}
        </BreadcrumbContext.Provider>
    );
}

export const useBreadcrumb = () => {
    const context = useContext(BreadcrumbContext);
    if (!context) {
        throw new Error("useBreadcrumb must be used within Provider");
    }
    return context;
};
