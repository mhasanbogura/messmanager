// One-off migration: collapse every duplicate account/member record onto its
// e-mail key (dots escaped as %2E, matching database.rules.json).
// Reads db-before.json, writes _merge/*.json and prints the planned changes.
// Usage: node merge-duplicates.js          (dry run: writes files + report)
//        node merge-duplicates.js --apply  (also pushes each file with firebase CLI)
const fs = require('fs');

const db = JSON.parse(fs.readFileSync('db-before.json', 'utf8'));
const ek = e => String(e || '').trim().toLowerCase().replace(/\./g, '%2E');
const isPseudo = k => String(k).startsWith('member_');
const outDir = '_merge';
fs.mkdirSync(outDir, { recursive: true });

const report = { members: [], permissions: [], online: [], users: [] };
const memberNameByEmail = {};   // emailKey -> { name, messes }
const uidToEmail = {};          // any old key -> e-mail

// ---------- mess members, permissions, online ----------
const messFiles = [];
for (const [mid, mess] of Object.entries(db.messes || {})) {
    const members = mess.members || {};
    const perms = mess.permissions || {};
    const online = mess.online || {};

    // group real member rows by e-mail key; keep name-only (member_…) rows as-is
    const groups = {};
    const nextMembers = {};
    for (const [key, row] of Object.entries(members)) {
        if (!row || isPseudo(key) || !row.email) { nextMembers[key] = row; continue; }
        const target = ek(row.email);
        (groups[target] = groups[target] || []).push([key, row]);
    }
    for (const [target, list] of Object.entries(groups)) {
        const score = r => (r.role === 'admin' ? 4 : 0) + ((r.status || 'active') === 'active' ? 2 : 0) + (r.phone ? 1 : 0);
        const winner = list.slice().sort((a, b) => score(b[1]) - score(a[1]) || ((a[1].joinedAt || Infinity) - (b[1].joinedAt || Infinity)))[0][1];
        const merged = {};
        list.forEach(([, r]) => Object.assign(merged, r));
        merged.name = winner.name || merged.name;
        merged.email = winner.email || merged.email;
        merged.role = list.some(([, r]) => r.role === 'admin') ? 'admin' : (winner.role || 'member');
        merged.status = list.some(([, r]) => (r.status || 'active') === 'active') ? 'active' : (winner.status || 'active');
        const times = list.map(([, r]) => r.joinedAt).filter(Boolean);
        merged.joinedAt = times.length ? Math.min(...times) : Date.now();
        nextMembers[target] = merged;
        list.forEach(([k, r]) => { uidToEmail[k] = target; });
        memberNameByEmail[target] = memberNameByEmail[target] || merged.name;
        if (list.length > 1 || list[0][0] !== target) {
            report.members.push({
                mid,
                to: `${target} (${merged.name}, ${merged.role}, ${merged.status})`,
                from: list.map(([k, r]) => `${k} (${r.name}, ${r.role || '-'}, ${r.status || '-'})`)
            });
        }
    }

    // permissions: move uid keys to the e-mail key, merge duplicates (true wins)
    const nextPerms = {};
    for (const [key, val] of Object.entries(perms)) {
        const target = uidToEmail[key] || (String(key).includes('@') ? key : key);
        const merged = Object.assign({}, nextPerms[target] || {}, val || {});
        for (const flag of Object.keys(val || {})) {
            if ((nextPerms[target] || {})[flag] === true || (val || {})[flag] === true) merged[flag] = true;
        }
        nextPerms[target] = merged;
        if (target !== key) report.permissions.push({ mid, from: key, to: target });
    }

    // online presence: same key rewrite, drop rows whose account no longer exists
    const nextOnline = {};
    for (const [key, val] of Object.entries(online)) {
        const target = uidToEmail[key] || (String(key).includes('@') ? key : null);
        if (!target) continue;
        nextOnline[target] = val;
        if (target !== key) report.online.push({ mid, from: key, to: target });
    }

    const write = (name, data) => {
        const file = `${outDir}/${mid}-${name}.json`;
        fs.writeFileSync(file, JSON.stringify(data, null, 2));
        messFiles.push({ path: `/messes/${mid}/${name}`, file });
    };
    write('members', nextMembers);
    write('permissions', nextPerms);
    write('online', nextOnline);
}

// ---------- user accounts ----------
const userGroups = {};
const keepKeys = [];
for (const [key, u] of Object.entries(db.users || {})) {
    const row = u || {};
    if (!row.email) {
        // keep legacy rows that still hold something; drop empty leftovers
        if (row.name || row.phone || Object.keys(row.messes || {}).length) keepKeys.push([key, row]);
        continue;
    }
    const target = ek(row.email);
    (userGroups[target] = userGroups[target] || []).push([key, row]);
}
const nextUsers = {};
for (const [key, row] of keepKeys) nextUsers[key] = row;
for (const [target, list] of Object.entries(userGroups)) {
    const merged = {};
    list.forEach(([, r]) => Object.assign(merged, r));
    const times = list.map(([, r]) => r.createdAt).filter(Boolean);
    merged.createdAt = times.length ? Math.min(...times) : Date.now();
    merged.email = list[0][1].email;
    merged.name = memberNameByEmail[target] || merged.name || 'Member';
    // union of mess indexes; admin role and earliest join win
    const messes = {};
    list.forEach(([, r]) => Object.entries(r.messes || {}).forEach(([mid, v]) => {
        const cur = messes[mid];
        if (!cur) messes[mid] = Object.assign({}, v);
        else if (v && v.role === 'admin' && cur.role !== 'admin') messes[mid] = Object.assign({}, cur, v);
        else if (v && v.joinedAt && (!cur.joinedAt || v.joinedAt < cur.joinedAt)) cur.joinedAt = v.joinedAt;
    }));
    if (Object.keys(messes).length) merged.messes = messes;
    const currentFromList = list.map(([, r]) => r.currentMess).filter(m => m && messes[m]);
    merged.currentMess = currentFromList[0] || (messes && Object.keys(messes)[0]) || merged.currentMess;
    if (!merged.currentMess) delete merged.currentMess;
    nextUsers[target] = merged;
    if (list.length > 1 || list[0][0] !== target) {
        report.users.push({
            to: `${target} (${merged.name}, currentMess ${merged.currentMess || '-'}, messes ${Object.keys(messes).join(',') || '-'})`,
            from: list.map(([k, r]) => `${k} (${r.name}, currentMess ${r.currentMess || '-'}, messes ${Object.keys(r.messes || {}).join(',') || '-'})`)
        });
    }
}
const usersFile = `${outDir}/users.json`;
fs.writeFileSync(usersFile, JSON.stringify(nextUsers, null, 2));

// ---------- report ----------
console.log(`accounts: ${Object.keys(db.users || {}).length} -> ${Object.keys(nextUsers).length}`);
report.users.forEach(r => console.log(`  user  ${r.from.join('  +  ')}\n     -> ${r.to}`));
report.members.forEach(r => console.log(`  member[${r.mid}]  ${r.from.join('  +  ')}\n     -> ${r.to}`));
report.permissions.forEach(r => console.log(`  perm[${r.mid}]  ${r.from} -> ${r.to}`));
report.online.forEach(r => console.log(`  online[${r.mid}]  ${r.from} -> ${r.to}`));

// "<db path>\t<file>" for every subtree to replace; applied by the shell
const plan = [{ path: '/users', file: usersFile }, ...messFiles].map(t => `${t.path}\t${t.file}`).join('\n') + '\n';
fs.writeFileSync(`${outDir}/plan.txt`, plan);
console.log('--- plan');
process.stdout.write(plan);
