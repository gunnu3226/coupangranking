import React, { useEffect, useMemo, useState } from 'react';
import { flushSync } from 'react-dom';
import { createRoot } from 'react-dom/client';
import './styles.css';

const COMPANIES = ['IPTIME', 'TPLINK', 'NETIS', 'MERCUSYS', 'ASUS', 'MI'];
const COMPANY_LABELS = {
  IPTIME: 'ipTIME',
  TPLINK: 'TP-Link',
  NETIS: 'NETIS',
  MERCUSYS: 'MERCUSYS',
  ASUS: 'ASUS',
  MI: 'MI'
};

function App() {
  const [path, setPath] = useState(window.location.pathname);
  const [parseResult, setParseResult] = useState(null);

  useEffect(() => {
    const onPopState = () => setPath(window.location.pathname);
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  const navigate = (nextPath) => {
    window.history.pushState({}, '', nextPath);
    setPath(nextPath);
  };

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1>쿠팡 랭킹 관리</h1>
          <p>HTML 파싱, 일자별 랭킹 조회, 관심 상품 관리를 한 곳에서 처리합니다.</p>
        </div>
        <nav className="top-nav">
          <NavButton path="/" currentPath={path} onClick={navigate}>HTML 입력</NavButton>
          <NavButton path="/saved-data" currentPath={path} onClick={navigate}>저장 데이터</NavButton>
          <NavButton path="/product-management" activePaths={["/product-management", "/favorite-order-edit"]} currentPath={path} onClick={navigate}>관심상품 관리</NavButton>
          <NavButton path="/today-result" currentPath={path} onClick={navigate}>오늘 결과</NavButton>
        </nav>
      </header>

      <main>
        {path === '/' && <HomePage onParsed={(result) => { setParseResult(result); navigate('/parse'); }} />}
        {path === '/parse' && <ResultPage result={parseResult} emptyMessage="아직 파싱한 결과가 없습니다. HTML 입력 화면에서 먼저 파싱하세요." />}
        {path === '/today-result' && <TodayResultPage />}
        {path === '/saved-data' && <SavedDataPage />}
        {(path === '/product-management' || path === '/favorite-order-edit') && <ProductManagementPage />}
      </main>
    </div>
  );
}

function NavButton({ path, activePaths = [path], currentPath, onClick, children }) {
  return (
    <button className={activePaths.includes(currentPath) ? 'nav-item active' : 'nav-item'} onClick={() => onClick(path)}>
      {children}
    </button>
  );
}

function HomePage({ onParsed }) {
  const [pages, setPages] = useState(['']);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const updatePage = (index, value) => {
    setPages((current) => current.map((page, pageIndex) => pageIndex === index ? value : page));
  };

  const addPage = () => setPages((current) => [...current, '']);
  const removePage = (index) => setPages((current) => current.filter((_, pageIndex) => pageIndex !== index));
  const clearAll = () => setPages(['']);

  const submit = async () => {
    const htmlPages = pages.map((page) => page.trim()).filter(Boolean);
    if (htmlPages.length === 0) {
      setError('HTML 텍스트를 입력해주세요.');
      return;
    }

    setLoading(true);
    setError('');
    try {
      const response = await fetch('/api/app/parse', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(htmlPages)
      });
      const result = await response.json();
      if (!response.ok || !result.success) {
        throw new Error(result.message || '파싱에 실패했습니다.');
      }
      onParsed(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="panel">
      <div className="panel-header">
        <div>
          <h2>쿠팡 HTML 입력</h2>
          <p>여러 페이지를 붙여 넣으면 전체 기준으로 상품 순위를 재계산해 저장합니다.</p>
        </div>
        <div className="actions">
          <button className="secondary-button" onClick={addPage}>페이지 추가</button>
          <button className="ghost-button" onClick={clearAll}>초기화</button>
        </div>
      </div>

      <div className="input-stack">
        {pages.map((page, index) => (
          <div className="html-input-group" key={index}>
            <div className="input-title">
              <strong>페이지 {index + 1}</strong>
              <span>{page.length.toLocaleString()}자</span>
              {pages.length > 1 && <button onClick={() => removePage(index)}>삭제</button>}
            </div>
            <textarea
              value={page}
              onChange={(event) => updatePage(index, event.target.value)}
              placeholder="쿠팡 상품 목록 HTML을 붙여넣으세요."
            />
          </div>
        ))}
      </div>

      {error && <div className="alert error">{error}</div>}
      <div className="submit-row">
        <button className="primary-button" onClick={submit} disabled={loading}>
          {loading ? '파싱 중...' : '상품 추출 및 저장'}
        </button>
      </div>
    </section>
  );
}

function TodayResultPage() {
  const { data, loading, error } = useApi('/api/app/today-result');
  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;
  return <ResultPage result={data} emptyMessage={data?.message || '오늘 저장된 데이터가 없습니다.'} />;
}

function ResultPage({ result, emptyMessage }) {
  const [tab, setTab] = useState('all');

  if (!result || !result.success) {
    return (
      <section className="panel">
        <h2>파싱 결과</h2>
        <div className="empty-state">{result?.message || emptyMessage}</div>
      </section>
    );
  }

  const tabs = [
    ['all', '전체 상품', result.allProducts || []],
    ['ranked', '랭킹 상품', result.rankedProducts || []],
    ['ad', '광고 상품', result.adProducts || []],
    ['normal', '일반 상품', result.normalProducts || []],
    ['tplink', 'TP-Link', result.tpLinkProducts || []],
    ['iptime', 'ipTIME', result.ipTimeProducts || []]
  ];
  const activeProducts = tabs.find(([key]) => key === tab)?.[2] || [];

  return (
    <section className="panel">
      <div className="panel-header">
        <div>
          <h2>파싱 결과</h2>
          <p>{result.message}</p>
        </div>
      </div>

      <StatsCards stats={[
        ['전체 상품', result.totalCount],
        ['랭킹 상품', result.rankedCount],
        ['광고 상품', result.adCount],
        ['일반 상품', result.normalCount]
      ]} />

      <div className="tabs">
        {tabs.map(([key, label, products]) => (
          <button key={key} className={tab === key ? 'tab active' : 'tab'} onClick={() => setTab(key)}>
            {label} <span>{products.length}</span>
          </button>
        ))}
      </div>

      <ProductCards products={activeProducts} />
      <CompactResultTable title="엑셀 복사용 테이블" products={activeProducts} />
    </section>
  );
}

function SavedDataPage() {
  const [date, setDate] = useState('');
  const [tab, setTab] = useState('router');
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('all');
  const [sortMode, setSortMode] = useState('ranking');
  const url = date ? `/api/app/saved-data?date=${encodeURIComponent(date)}` : '/api/app/saved-data';
  const { data, loading, error, reload } = useApi(url);

  useEffect(() => {
    if (data?.selectedDate && !date) {
      setDate(data.selectedDate);
    }
  }, [data, date]);


  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  const displayOrderMap = data?.displayOrderMap || {};
  const productsByCompany = data?.productsByCompany || {};
  const favoriteProductsByCompany = data?.favoriteProductsByCompany || {};
  const currentProducts = tab === 'favorites' ? data?.favoriteAllProducts || [] : data?.allNonAdProducts || [];

  const filteredCurrentProducts = applySavedFilters(currentProducts, search, filter, sortMode, displayOrderMap);

  return (
    <section className="panel">
      <div className="panel-header">
        <div>
          <h2>저장 데이터</h2>
          <p>선택 날짜의 상품 랭킹과 관심 상품 목록을 확인합니다.</p>
        </div>
        <div className="controls-row">
          <select value={date} onChange={(event) => setDate(event.target.value)}>
            {(data?.availableDates || []).map((availableDate) => (
              <option key={availableDate} value={availableDate}>{availableDate}</option>
            ))}
          </select>
        </div>
      </div>

      <StatsCards stats={[
        ['선택 날짜', data?.selectedDate || '-'],
        ['회사별 상품', data?.totalCount || 0],
        ['광고 제외 전체', data?.allNonAdProducts?.length || 0],
        ['관심 상품', data?.favoriteAllProducts?.length || 0]
      ]} />

      <div className="tabs">
        <button className={tab === 'router' ? 'tab active' : 'tab'} onClick={() => setTab('router')}>공유기</button>
        <button className={tab === 'favorites' ? 'tab active' : 'tab'} onClick={() => setTab('favorites')}>선택한 상품</button>
      </div>

      <div className="toolbar">
        <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="itemId 또는 상품명 검색" />
        <select value={filter} onChange={(event) => setFilter(event.target.value)}>
          <option value="all">전체</option>
          <option value="ad">광고만</option>
          <option value="normal">광고 제외</option>
        </select>
        {tab === 'favorites' && (
          <select value={sortMode} onChange={(event) => setSortMode(event.target.value)}>
            <option value="ranking">순위순</option>
            <option value="custom">내가 정한 순서</option>
          </select>
        )}
      </div>

      <SavedDataTable
        title={tab === 'favorites' ? '선택한 상품 전체' : '전체 순위'}
        products={filteredCurrentProducts}
      />

      <CompanySections
        productsByCompany={tab === 'favorites' ? favoriteProductsByCompany : productsByCompany}
        search={search}
        filter={filter}
        sortMode={sortMode}
        displayOrderMap={displayOrderMap}
      />
    </section>
  );
}

function ProductManagementPage() {
  const productsApi = useApi('/api/app/products');
  const favoritesApi = useApi('/api/app/favorites/full');
  const [search, setSearch] = useState('');
  const [companyFilter, setCompanyFilter] = useState('all');
  const [selectionFilter, setSelectionFilter] = useState('all');
  const [selectedProducts, setSelectedProducts] = useState([]);
  const [draggingId, setDraggingId] = useState(null);
  const [dragOverId, setDragOverId] = useState(null);
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setSelectedProducts(favoritesApi.data?.products || []);
  }, [favoritesApi.data]);

  const allProducts = productsApi.data?.allProducts || [];
  const selectedIds = useMemo(() => new Set(selectedProducts.map((product) => product.id)), [selectedProducts]);
  const savedOrderIds = useMemo(() => (favoritesApi.data?.products || []).map((product) => product.id), [favoritesApi.data]);
  const selectedOrderIds = useMemo(() => selectedProducts.map((product) => product.id), [selectedProducts]);
  const isDirty = !sameIds(savedOrderIds, selectedOrderIds);

  const visibleProducts = useMemo(() => {
    let products = filterProducts(allProducts, search);
    if (companyFilter !== 'all') {
      products = products.filter((product) => product.company === companyFilter);
    }
    if (selectionFilter === 'selected') {
      products = products.filter((product) => selectedIds.has(product.id));
    }
    if (selectionFilter === 'unselected') {
      products = products.filter((product) => !selectedIds.has(product.id));
    }
    return [...products].sort((a, b) => {
      const companyCompare = String(a.company || '').localeCompare(String(b.company || ''));
      if (companyCompare !== 0) return companyCompare;
      return String(a.productName || '').localeCompare(String(b.productName || ''), 'ko');
    });
  }, [allProducts, search, companyFilter, selectionFilter, selectedIds]);

  const updateSelectedProducts = (updater, animate = false) => {
    if (animate && document.startViewTransition) {
      document.startViewTransition(() => {
        flushSync(() => setSelectedProducts(updater));
      });
      return;
    }
    setSelectedProducts(updater);
  };

  const toggleProduct = (product) => {
    setMessage('');
    updateSelectedProducts((current) => {
      if (current.some((item) => item.id === product.id)) {
        return current.filter((item) => item.id !== product.id);
      }
      return [...current, product];
    });
  };

  const moveSelected = (fromIndex, toIndex) => {
    setMessage('');
    updateSelectedProducts((current) => reorderProducts(current, fromIndex, toIndex), true);
  };

  const moveSelectedById = (draggedId, targetId) => {
    if (!draggedId || draggedId === targetId) return;
    setMessage('');
    updateSelectedProducts((current) => {
      const fromIndex = current.findIndex((item) => item.id === draggedId);
      const toIndex = current.findIndex((item) => item.id === targetId);
      return reorderProducts(current, fromIndex, toIndex);
    }, true);
  };

  const removeSelected = (productId) => {
    setMessage('');
    updateSelectedProducts((current) => current.filter((product) => product.id !== productId), true);
  };

  const saveFavorites = async () => {
    setSaving(true);
    setMessage('');
    try {
      await saveFavoriteIds(selectedProducts.map((product) => product.id));
      await Promise.all([productsApi.reload(), favoritesApi.reload()]);
      setMessage(`${selectedProducts.length}개 관심상품과 표시 순서를 저장했습니다.`);
    } catch (err) {
      setMessage(err.message || '관심상품 저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  if (productsApi.loading || favoritesApi.loading) return <Loading />;
  if (productsApi.error) return <ErrorMessage message={productsApi.error} />;
  if (favoritesApi.error) return <ErrorMessage message={favoritesApi.error} />;

  return (
    <section className="panel favorite-manager">
      <div className="panel-header">
        <div>
          <h2>관심상품 관리</h2>
          <p>저장된 상품을 고르고, 선택한 상품의 표시 순서를 한 화면에서 직접 정합니다.</p>
        </div>
        <div className="actions">
          <button className="ghost-button" onClick={() => { setSelectedProducts(favoritesApi.data?.products || []); setMessage('변경 전 상태로 되돌렸습니다.'); }} disabled={!isDirty || saving}>되돌리기</button>
          <button className="primary-button" onClick={saveFavorites} disabled={!isDirty || saving}>{saving ? '저장 중...' : '선택과 순서 저장'}</button>
        </div>
      </div>

      <StatsCards stats={[
        ['전체 상품', productsApi.data?.totalCount || 0],
        ['현재 보기', visibleProducts.length],
        ['선택 상품', selectedProducts.length],
        ['저장 상태', isDirty ? '변경됨' : '저장됨']
      ]} />

      {message && <div className={message.includes('실패') ? 'alert error' : 'alert'}>{message}</div>}

      <div className="favorite-workspace">
        <section className="favorite-catalog">
          <div className="workspace-header">
            <div>
              <h3>저장된 상품</h3>
              <p>검색·필터 후 추가/제거해도 선택 목록 전체가 유지됩니다.</p>
            </div>
          </div>
          <div className="toolbar favorite-toolbar">
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="itemId 또는 상품명 검색" />
            <select value={companyFilter} onChange={(event) => setCompanyFilter(event.target.value)}>
              <option value="all">전체 회사</option>
              {COMPANIES.map((company) => <option key={company} value={company}>{COMPANY_LABELS[company]}</option>)}
            </select>
            <select value={selectionFilter} onChange={(event) => setSelectionFilter(event.target.value)}>
              <option value="all">전체 상품</option>
              <option value="selected">선택됨</option>
              <option value="unselected">미선택</option>
            </select>
          </div>
          <ProductCatalogTable products={visibleProducts} selectedIds={selectedIds} onToggle={toggleProduct} />
        </section>

        <section className="selected-panel">
          <div className="workspace-header">
            <div>
              <h3>선택한 상품 순서</h3>
              <p>여기에 보이는 순서가 저장 데이터의 “내가 정한 순서”로 저장됩니다.</p>
            </div>
            <button className="ghost-button" onClick={() => { setSelectedProducts([]); setMessage(''); }} disabled={selectedProducts.length === 0}>선택 모두 비우기</button>
          </div>
          <SelectedOrderList
            products={selectedProducts}
            draggingId={draggingId}
            setDraggingId={setDraggingId}
            dragOverId={dragOverId}
            setDragOverId={setDragOverId}
            onMove={moveSelected}
            onMoveById={moveSelectedById}
            onRemove={removeSelected}
          />
        </section>
      </div>
    </section>
  );
}

function ProductCatalogTable({ products, selectedIds, onToggle }) {
  if (products.length === 0) return <div className="empty-state">조건에 맞는 상품이 없습니다.</div>;
  return (
    <div className="table-wrap catalog-table-wrap">
      <table className="catalog-table">
        <thead>
          <tr>
            <th>상태</th>
            <th>itemId</th>
            <th>상품명</th>
            <th>회사</th>
            <th>작업</th>
          </tr>
        </thead>
        <tbody>
          {products.map((product) => {
            const selected = selectedIds.has(product.id);
            return (
              <tr key={product.id} className={selected ? 'selected-row' : ''}>
                <td className="center">{selected ? <span className="status-pill selected">선택됨</span> : <span className="status-pill">미선택</span>}</td>
                <td className="mono">{product.itemId}</td>
                <td>{product.productName}</td>
                <td>{COMPANY_LABELS[product.company] || product.company || ''}</td>
                <td className="center">
                  <button className={selected ? 'ghost-button' : 'secondary-button'} onClick={() => onToggle(product)}>{selected ? '제거' : '추가'}</button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function SelectedOrderList({ products, draggingId, setDraggingId, dragOverId, setDragOverId, onMove, onMoveById, onRemove }) {
  if (products.length === 0) {
    return <div className="empty-state">왼쪽 목록에서 관심상품을 추가하면 여기에 순서대로 표시됩니다.</div>;
  }

  return (
    <div className={draggingId ? 'order-list selected-order-list drag-active' : 'order-list selected-order-list'}>
      {products.map((product, index) => (
        <div
          key={product.id}
          className={orderItemClass(product.id, draggingId, dragOverId)}
          style={{ viewTransitionName: `favorite-order-${product.id}` }}
          draggable
          onDragStart={(event) => {
            event.dataTransfer.effectAllowed = 'move';
            event.dataTransfer.setData('text/plain', String(product.id));
            setDraggingId(product.id);
          }}
          onDragEnter={() => {
            if (draggingId && draggingId !== product.id) {
              setDragOverId(product.id);
              onMoveById(draggingId, product.id);
            }
          }}
          onDragOver={(event) => {
            event.preventDefault();
            event.dataTransfer.dropEffect = 'move';
          }}
          onDrop={(event) => {
            event.preventDefault();
            setDragOverId(null);
            setDraggingId(null);
          }}
          onDragEnd={() => {
            setDragOverId(null);
            setDraggingId(null);
          }}
        >
          <span className="order-number">{index + 1}</span>
          <div className="order-info">
            <strong>{product.productName}</strong>
            <span>{product.itemId} · {COMPANY_LABELS[product.company] || product.company || '-'}</span>
          </div>
          <div className="order-actions">
            <button onClick={() => onMove(index, index - 1)} disabled={index === 0}>위</button>
            <button onClick={() => onMove(index, index + 1)} disabled={index === products.length - 1}>아래</button>
            <button onClick={() => onRemove(product.id)}>제거</button>
          </div>
        </div>
      ))}
    </div>
  );
}

function ProductCards({ products }) {
  if (!products || products.length === 0) return <div className="empty-state">상품이 없습니다.</div>;
  return (
    <div className="product-grid">
      {products.map((product, index) => (
        <article className="product-card" key={`${product.productId}-${product.itemId}-${index}`}>
          <img src={product.imageUrl || ''} alt="" onError={(event) => { event.currentTarget.style.display = 'none'; }} />
          <div className="product-body">
            <div className="badges">
              {product.ranking != null && <span className="badge rank">랭킹 {product.ranking}</span>}
              {isAd(product) && <span className="badge ad">광고</span>}
            </div>
            <h3>{product.productName || '-'}</h3>
            <div className="price-line">
              {product.originalPrice && <span className="original">{product.originalPrice}</span>}
              {product.discountRate && <span className="discount">{product.discountRate}</span>}
            </div>
            <strong className="price">{product.currentPrice || '-'}</strong>
            <div className="meta">
              <span>{deliveryLabel(product.deliveryMethod)}</span>
              {product.reviewCount && <span>리뷰 {product.reviewCount}</span>}
            </div>
          </div>
        </article>
      ))}
    </div>
  );
}

function CompactResultTable({ title, products }) {
  if (!products || products.length === 0) return null;
  return (
    <DataBlock title={title} actions={<CopyButton tableId="compact-result-table" />}>
      <table id="compact-result-table">
        <thead>
          <tr>
            <th>상품명</th>
            <th>노출순위</th>
            <th>판가</th>
            <th>댓글수</th>
          </tr>
        </thead>
        <tbody>
          {products.map((product, index) => (
            <tr key={`${product.productId}-${index}`}>
              <td>{product.productName}</td>
              <td className="center">{product.ranking ?? ''}</td>
              <td className="right">{stripWon(product.currentPrice)}</td>
              <td className="right">{stripParens(product.reviewCount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </DataBlock>
  );
}

function SavedDataTable({ title, products }) {
  return (
    <DataBlock
      title={`${title} (${products.length}개)`}
      actions={<CopyButton tableId={`saved-${slug(title)}`} />}
    >
      <SavedTable id={`saved-${slug(title)}`} products={products} />
    </DataBlock>
  );
}

function CompanySections({ productsByCompany, search, filter, sortMode, displayOrderMap }) {
  return (
    <div className="company-sections">
      {COMPANIES.map((company) => {
        const products = applySavedFilters(productsByCompany?.[company] || [], search, filter, sortMode, displayOrderMap);
        if (products.length === 0) return null;
        return (
          <SavedDataTable
            key={company}
            title={COMPANY_LABELS[company]}
            products={products}
          />
        );
      })}
    </div>
  );
}

function SavedTable({ id, products }) {
  if (products.length === 0) return <div className="empty-state">표시할 상품이 없습니다.</div>;
  return (
    <table id={id}>
      <thead>
        <tr>
          <th>itemId</th>
          <th>상품명</th>
          <th>순위</th>
          <th>가격</th>
          <th>리뷰</th>
          <th>배송</th>
        </tr>
      </thead>
      <tbody>
        {products.map((product, index) => (
          <tr key={`${product.id}-${index}`} className={isAd(product) ? 'ad-row' : ''}>
            <td className="mono">{product.itemId}</td>
            <td>
              {product.productUrl ? <a href={product.productUrl} target="_blank" rel="noreferrer">{product.productName}</a> : product.productName}
            </td>
            <td className="center">{product.ranking ?? ''}</td>
            <td className="right">{product.currentPrice || ''}</td>
            <td className="right">{product.reviewCount || ''}</td>
            <td className="center">{deliveryLabel(product.deliveryMethod)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function DataBlock({ title, actions, children, className = '' }) {
  return (
    <section className={`data-block ${className}`}>
      <div className="data-block-header">
        <h3>{title}</h3>
        <div className="table-actions">{actions}</div>
      </div>
      <div className="table-wrap">{children}</div>
    </section>
  );
}

function StatsCards({ stats }) {
  return (
    <div className="stats-grid">
      {stats.map(([label, value]) => (
        <div className="stat-card" key={label}>
          <span>{label}</span>
          <strong>{value}</strong>
        </div>
      ))}
    </div>
  );
}

function CopyButton({ tableId }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    const table = document.getElementById(tableId);
    if (!table) return;
    const text = [...table.querySelectorAll('tr')]
      .map((row) => [...row.children].map((cell) => cell.innerText.trim()).join('\t'))
      .join('\n');
    await navigator.clipboard.writeText(text);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1200);
  };
  return <button className="ghost-button" onClick={copy}>{copied ? '복사됨' : '표 복사'}</button>;
}

function Loading() {
  return <section className="panel"><div className="empty-state">데이터를 불러오는 중입니다.</div></section>;
}

function ErrorMessage({ message }) {
  return <section className="panel"><div className="alert error">{message}</div></section>;
}

function useApi(url) {
  const [state, setState] = useState({ data: null, loading: true, error: '' });
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let alive = true;
    setState((current) => ({ ...current, loading: true, error: '' }));
    fetch(url)
      .then(async (response) => {
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || '요청에 실패했습니다.');
        return data;
      })
      .then((data) => alive && setState({ data, loading: false, error: '' }))
      .catch((error) => alive && setState({ data: null, loading: false, error: error.message }));
    return () => {
      alive = false;
    };
  }, [url, version]);

  return { ...state, reload: () => setVersion((current) => current + 1) };
}

async function saveFavoriteIds(productIds) {
  const response = await fetch('/api/favorites', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ productIds })
  });
  const result = await response.json();
  if (!response.ok || !result.success) {
    throw new Error(result.message || '선택 상품 저장에 실패했습니다.');
  }
  return result;
}

function filterProducts(products, search) {
  const keyword = normalize(search);
  if (!keyword) return products;
  return products.filter((product) => normalize(`${product.itemId || ''} ${product.productName || ''}`).includes(keyword));
}

function reorderProducts(products, fromIndex, toIndex) {
  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex || toIndex >= products.length) return products;
  const next = [...products];
  const [item] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, item);
  return next;
}

function orderItemClass(productId, draggingId, dragOverId) {
  return [
    'order-item',
    draggingId === productId ? 'dragging' : '',
    dragOverId === productId ? 'drag-over' : ''
  ].filter(Boolean).join(' ');
}

function sameIds(a, b) {
  if (a.length !== b.length) return false;
  return a.every((id, index) => id === b[index]);
}

function applySavedFilters(products, search, filter, sortMode, displayOrderMap) {
  let result = filterProducts(products, search);
  if (filter === 'ad') result = result.filter(isAd);
  if (filter === 'normal') result = result.filter((product) => !isAd(product));
  return [...result].sort((a, b) => {
    if (sortMode === 'custom') {
      return (displayOrderMap[a.id] || 999999) - (displayOrderMap[b.id] || 999999);
    }
    return (a.ranking || 999999) - (b.ranking || 999999);
  });
}

function normalize(value) {
  return String(value || '').replace(/\s/g, '').toLowerCase();
}

function isAd(product) {
  return Boolean(product?.ad ?? product?.isAd);
}

function deliveryLabel(method) {
  if (method === 'ROCKET_DELIVERY') return '로켓배송';
  if (method === 'ROCKET_MERCHANT') return '판매자로켓';
  if (method === 'NORMAL_DELIVERY') return '일반배송';
  return '';
}

function stripWon(value) {
  return String(value || '').replace('원', '').trim();
}

function stripParens(value) {
  return String(value || '').replace(/[()]/g, '').trim();
}

function slug(value) {
  return String(value).replace(/[^a-zA-Z0-9가-힣]/g, '-');
}

createRoot(document.getElementById('root')).render(<App />);
